package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
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

@Component
@Profile("otp-console")
public class ConsoleVerificarCelularAdapter implements VerificarCelularPort {
    private static final Logger log = LoggerFactory.getLogger(ConsoleVerificarCelularAdapter.class);
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

    public ConsoleVerificarCelularAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${napa.auth.verification.console-code}") String consoleCode
    ) {
        if (consoleCode == null || !consoleCode.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("The console verification code must contain exactly six digits");
        }
        this.redisTemplate = redisTemplate;
        this.expectedCode = consoleCode.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void solicitar(String celular, EstadoVerificacion estado, String ipAddress) {
        try {
            redisTemplate.opsForValue().set(key(celular, estado), "0", CODE_TTL);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public VerificacionResultado confirmar(String celular, EstadoVerificacion estado, String codigo) {
        String key = key(celular, estado);
        if (codigo != null && MessageDigest.isEqual(
                expectedCode,
                codigo.getBytes(StandardCharsets.UTF_8)
        )) {
            try {
                Boolean consumed = redisTemplate.delete(key);
                return Boolean.TRUE.equals(consumed)
                        ? new VerificacionResultado(VerificacionResultado.Estado.APROBADA, MAX_ATTEMPTS)
                        : new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0);
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

    private String key(String celular, EstadoVerificacion estado) {
        return "auth:verification:console:" + digest(celular + ":" + estado.name());
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
