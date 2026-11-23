package co.edu.napa.cuentas.infrastructure.adapters.out.cache;

import co.edu.napa.cuentas.application.ports.out.EsperaReenvioPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Espera creciente en Redis: tras el envío n la siguiente espera es 2^n minutos (2, 4, 8…),
 * con un tope de una hora. La cuenta de envíos se reinicia 24 horas después del último.
 */
@Component
public class RedisEsperaReenvioAdapter implements EsperaReenvioPort {
    private static final Logger log = LoggerFactory.getLogger(RedisEsperaReenvioAdapter.class);
    static final long ESPERA_INICIAL_SEGUNDOS = 120;
    static final long ESPERA_MAXIMA_SEGUNDOS = 3600;
    private static final long VENTANA_SEGUNDOS = 86_400;

    /** KEYS: [espera, envios]. ARGV: [inicial, maxima, ventana]. Devuelve la espera fijada. */
    private static final RedisScript<Long> REGISTRAR = new DefaultRedisScript<>(
            """
            local n = redis.call('incr', KEYS[2])
            redis.call('expire', KEYS[2], tonumber(ARGV[3]))
            local espera = math.floor(tonumber(ARGV[1]) * (2 ^ (n - 1)))
            if espera > tonumber(ARGV[2]) then espera = tonumber(ARGV[2]) end
            redis.call('set', KEYS[1], '1', 'EX', espera)
            return espera
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisEsperaReenvioAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void exigirDisponible(String celular, CanalVerificacion canal) {
        Long ttl;
        try {
            ttl = redisTemplate.getExpire(esperaKey(celular, canal));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        if (ttl != null && ttl > 0) {
            throw new DomainException(
                    "RATE_LIMITED",
                    "Espera " + formato(ttl) + " para pedir otro código.",
                    Map.of("retryAfter", ttl)
            );
        }
    }

    @Override
    public long registrarEnvio(String celular, CanalVerificacion canal) {
        try {
            Long espera = redisTemplate.execute(
                    REGISTRAR,
                    List.of(esperaKey(celular, canal), "verif-espera:n:" + digest(celular + ":" + canal.name())),
                    Long.toString(ESPERA_INICIAL_SEGUNDOS),
                    Long.toString(ESPERA_MAXIMA_SEGUNDOS),
                    Long.toString(VENTANA_SEGUNDOS)
            );
            return espera == null ? ESPERA_INICIAL_SEGUNDOS : espera;
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    static String formato(long segundos) {
        if (segundos < 60) {
            return segundos == 1 ? "1 segundo" : segundos + " segundos";
        }
        long minutos = (segundos + 59) / 60;
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }

    private String esperaKey(String celular, CanalVerificacion canal) {
        return "verif-espera:bloqueo:" + digest(celular + ":" + canal.name());
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private DomainException unavailable(Exception exception) {
        log.error("Redis unavailable while checking the resend wait", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible procesar la verificación en este momento."
        );
    }
}
