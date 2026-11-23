package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.CodigoVisiblePort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Base de los proveedores en los que Ñapa genera y valida el código (en Redis, 10 minutos, 5 intentos)
 * y solo delega la entrega. Si la entrega no sale por un canal externo, el código queda visible
 * para mostrarlo como imagen en la app.
 */
public abstract class CodigoPropioVerificacionAdapter implements VerificarCelularPort, CodigoVisiblePort {
    private static final Logger log = LoggerFactory.getLogger(CodigoPropioVerificacionAdapter.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "auth:verification:imagen:";
    private static final RedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[1]) then
                redis.call('DEL', KEYS[1])
                return 0
            end
            return tonumber(ARGV[1]) - attempts
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    protected CodigoPropioVerificacionAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Entrega el código. Devuelve true si salió por un canal externo (SMS) y false si la app debe
     * mostrarlo como imagen. Lanza EXTERNAL_SERVICE_UNAVAILABLE si no hay forma de entregarlo.
     */
    protected abstract boolean entregar(String celular, PropositoVerificacion proposito, String codigo);

    protected static DomainException noDisponible(String mensaje) {
        return new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", mensaje);
    }

    @Override
    public EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal) {
        String codigo = "%06d".formatted(random.nextInt(1_000_000));
        String proveedorId = "codigo-" + UUID.randomUUID();
        String key = key(celular, proposito);
        boolean externo = entregar(celular, proposito, codigo);
        try {
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, Map.of(
                    "code", codigo,
                    "prov", proveedorId,
                    "attempts", "0",
                    "visible", externo ? "0" : "1"
            ));
            redisTemplate.expire(key, CODE_TTL);
            redisTemplate.opsForValue().set(PREFIX + "prov:" + proveedorId, key, CODE_TTL);
            return new EnvioVerificacion(proveedorId);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<String> codigoVigente(String proveedorId) {
        try {
            String key = redisTemplate.opsForValue().get(PREFIX + "prov:" + proveedorId);
            if (key == null) {
                return Optional.empty();
            }
            List<Object> values = redisTemplate.opsForHash().multiGet(key, List.of("prov", "code", "visible"));
            if (values.size() != 3 || !proveedorId.equals(values.get(0)) || values.get(1) == null
                    || !"1".equals(values.get(2))) {
                return Optional.empty();
            }
            return Optional.of(values.get(1).toString());
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo) {
        return confirmarConResultado(celular, proposito, codigo).estado();
    }

    @Override
    public VerificacionResultado confirmar(String celular, Enum<?> estado, String codigo) {
        PropositoVerificacion proposito = estado instanceof PropositoVerificacion p
                ? p
                : PropositoVerificacion.valueOf(estado.name());
        return confirmarConResultado(celular, proposito, codigo);
    }

    private VerificacionResultado confirmarConResultado(
            String celular,
            PropositoVerificacion proposito,
            String codigo
    ) {
        String key = key(celular, proposito);
        try {
            Object expected = redisTemplate.opsForHash().get(key, "code");
            if (expected == null) {
                return new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0);
            }
            if (codigo != null && MessageDigest.isEqual(
                    expected.toString().getBytes(StandardCharsets.UTF_8),
                    codigo.getBytes(StandardCharsets.UTF_8)
            )) {
                return new VerificacionResultado(
                        Boolean.TRUE.equals(redisTemplate.delete(key))
                                ? VerificacionResultado.Estado.APROBADA
                                : VerificacionResultado.Estado.VENCIDA,
                        0
                );
            }
            return registrarFallo(key);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private VerificacionResultado registrarFallo(String key) {
        Long remaining = redisTemplate.execute(RECORD_FAILURE, List.of(key), Integer.toString(MAX_ATTEMPTS));
        if (remaining == null) {
            throw unavailable(new IllegalStateException("Redis returned no verification attempt result"));
        }
        if (remaining < 0) {
            return new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0);
        }
        if (remaining == 0) {
            return new VerificacionResultado(VerificacionResultado.Estado.AGOTADA, 0);
        }
        return new VerificacionResultado(VerificacionResultado.Estado.INCORRECTA, remaining.intValue());
    }

    private String key(String celular, PropositoVerificacion proposito) {
        return PREFIX + digest(celular + ":" + proposito.name());
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
        log.error("Redis unavailable while processing image phone verification", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible procesar la verificación del celular en este momento."
        );
    }
}
