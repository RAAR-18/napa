package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
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
import java.util.UUID;

/** Proveedor simulado exclusivo de desarrollo: código configurado y estado temporal en Redis. */
@Component
@Profile("otp-console & !prod")
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "console")
public class ConsolaVerificacionAdapter implements VerificarCelularPort {
    private static final Logger log = LoggerFactory.getLogger(ConsolaVerificacionAdapter.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final RedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local attempts = redis.call('INCR', KEYS[1])
            if attempts >= tonumber(ARGV[1]) then
                redis.call('DEL', KEYS[1])
                return 0
            end
            return tonumber(ARGV[1]) - attempts
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final byte[] expectedCode;

    public ConsolaVerificacionAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${napa.verification.console-code:}") String consoleCode
    ) {
        if (consoleCode == null || !consoleCode.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("The console verification code must contain exactly six digits");
        }
        this.redisTemplate = redisTemplate;
        this.expectedCode = consoleCode.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal) {
        try {
            redisTemplate.opsForValue().set(key(celular, proposito), "0", CODE_TTL);
            return new EnvioVerificacion("console-" + UUID.randomUUID());
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void solicitar(String celular, Enum<?> estado, String ipAddress) {
        PropositoVerificacion proposito = proposito(estado);
        iniciar(celular, proposito, CanalVerificacion.WHATSAPP);
    }

    @Override
    public VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo) {
        return confirmarConResultado(celular, proposito, codigo).estado();
    }

    @Override
    public VerificacionResultado confirmar(String celular, Enum<?> estado, String codigo) {
        return confirmarConResultado(celular, proposito(estado), codigo);
    }

    private VerificacionResultado confirmarConResultado(
            String celular,
            PropositoVerificacion proposito,
            String codigo
    ) {
        String key = key(celular, proposito);
        if (codigo != null && MessageDigest.isEqual(
                expectedCode,
                codigo.getBytes(StandardCharsets.UTF_8)
        )) {
            try {
                Boolean consumed = redisTemplate.delete(key);
                return new VerificacionResultado(
                        Boolean.TRUE.equals(consumed)
                                ? VerificacionResultado.Estado.APROBADA
                                : VerificacionResultado.Estado.VENCIDA,
                        0
                );
            } catch (DataAccessException exception) {
                throw unavailable(exception);
            }
        }

        try {
            Long attemptsRemaining = redisTemplate.execute(
                    RECORD_FAILURE,
                    List.of(key),
                    Integer.toString(MAX_ATTEMPTS)
            );
            if (attemptsRemaining == null) {
                throw unavailable(new IllegalStateException("Redis returned no verification attempt result"));
            }
            if (attemptsRemaining < 0) {
                return new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0);
            }
            if (attemptsRemaining == 0) {
                return new VerificacionResultado(VerificacionResultado.Estado.AGOTADA, 0);
            }
            return new VerificacionResultado(VerificacionResultado.Estado.INCORRECTA, attemptsRemaining.intValue());
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private PropositoVerificacion proposito(Enum<?> estado) {
        return estado instanceof PropositoVerificacion p
                ? p
                : PropositoVerificacion.valueOf(((EstadoVerificacion) estado).name());
    }

    private String key(String celular, PropositoVerificacion proposito) {
        return "auth:verification:console:" + digest(celular + ":" + proposito.name());
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
        log.error("Redis unavailable while processing console phone verification", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible procesar la verificación del celular en este momento."
        );
    }
}
