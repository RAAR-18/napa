package co.edu.napa.cuentas.infrastructure.adapters.out.cache;

import co.edu.napa.cuentas.application.ports.out.LimitadorVerificacionPort;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Límites de RF-70 en Redis. Los celulares y las IP se guardan con hash para no dejar números en claro.
 * Envíos: 1 por minuto y 5 por hora por celular, y 20 por hora por IP.
 */
@Component
public class RedisLimitadorVerificacionAdapter implements LimitadorVerificacionPort {
    private static final Logger log = LoggerFactory.getLogger(RedisLimitadorVerificacionAdapter.class);
    private static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

    /**
     * KEYS: un contador por límite. ARGV: pares (límite, ventana en segundos), en el mismo orden.
     * Si algún contador ya llegó a su límite devuelve los segundos de espera y no cuenta nada;
     * si no, suma uno a todos. Devuelve 0 cuando el envío está permitido.
     */
    private static final RedisScript<Long> REGISTRAR_ENVIO = new DefaultRedisScript<>(
            """
            local retry = 0
            for i = 1, #KEYS do
                local count = tonumber(redis.call('get', KEYS[i]) or '0')
                if count >= tonumber(ARGV[(i - 1) * 2 + 1]) then
                    local ttl = redis.call('ttl', KEYS[i])
                    if ttl < 1 then ttl = 1 end
                    if ttl > retry then retry = ttl end
                end
            end
            if retry > 0 then return retry end
            for i = 1, #KEYS do
                local count = redis.call('incr', KEYS[i])
                if count == 1 then redis.call('expire', KEYS[i], tonumber(ARGV[(i - 1) * 2 + 2])) end
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisLimitadorVerificacionAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void registrarEnvio(String celular, String ipAddress) {
        try {
            Long retryAfter = redisTemplate.execute(
                    REGISTRAR_ENVIO,
                    List.of(
                            "verif-rl:min:" + digest(celular),
                            "verif-rl:hora:" + digest(celular),
                            "verif-rl:ip:" + digest(ipAddress == null ? "desconocida" : ipAddress)
                    ),
                    "1", "60",
                    "5", "3600",
                    "20", "3600"
            );
            if (retryAfter != null && retryAfter > 0) {
                throw new DomainException(
                        "RATE_LIMITED",
                        "Demasiadas solicitudes de código. Inténtalo más tarde.",
                        Map.of("retryAfter", retryAfter)
                );
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public int intentosRestantes(String celular, PropositoVerificacion proposito) {
        try {
            String valor = redisTemplate.opsForValue().get(claveIntentos(celular, proposito));
            int usados = valor == null ? 0 : Integer.parseInt(valor);
            return Math.max(0, INTENTOS_MAXIMOS - usados);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public int registrarIntentoFallido(String celular, PropositoVerificacion proposito) {
        try {
            String clave = claveIntentos(celular, proposito);
            Long usados = redisTemplate.opsForValue().increment(clave);
            if (usados != null && usados == 1L) {
                redisTemplate.expire(clave, VIGENCIA_CODIGO);
            }
            return Math.max(0, INTENTOS_MAXIMOS - (usados == null ? 0 : usados.intValue()));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void reiniciarIntentos(String celular, PropositoVerificacion proposito) {
        try {
            redisTemplate.delete(claveIntentos(celular, proposito));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private String claveIntentos(String celular, PropositoVerificacion proposito) {
        return "verif-att:" + digest(celular) + ":" + proposito.name();
    }

    private String digest(String valor) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private DomainException unavailable(DataAccessException exception) {
        log.error("Redis no disponible al aplicar los límites de verificación", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible validar los límites de verificación en este momento."
        );
    }
}