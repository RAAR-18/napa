package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.VerificationRateLimitPort;
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
import java.util.UUID;

@Component
public class RedisVerificationRateLimitAdapter implements VerificationRateLimitPort {
    private static final Logger log = LoggerFactory.getLogger(RedisVerificationRateLimitAdapter.class);
    private static final RedisScript<Long> CHECK_AND_RECORD = new DefaultRedisScript<>(
            """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local retry = 0

            for _, key in ipairs(KEYS) do
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now - 3600000)
                local latest = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                if #latest > 0 then
                    local minuteRetry = tonumber(latest[2]) + 60000 - now
                    if minuteRetry > retry then retry = minuteRetry end
                end

                if redis.call('ZCARD', key) >= 5 then
                    local earliest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                    local hourRetry = tonumber(earliest[2]) + 3600000 - now
                    if hourRetry > retry then retry = hourRetry end
                end
            end

            if retry > 0 then return math.max(1, math.ceil(retry / 1000)) end

            for _, key in ipairs(KEYS) do
                redis.call('ZADD', key, now, ARGV[1])
                redis.call('EXPIRE', key, 3601)
            end
            return 0
            """,
            Long.class
    );
    private final StringRedisTemplate redisTemplate;

    public RedisVerificationRateLimitAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void checkAndRecord(String celular, String ipAddress) {
        if (celular == null || celular.isBlank() || ipAddress == null || ipAddress.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "El celular y la dirección IP son obligatorios.");
        }

        try {
            Long retryAfter = redisTemplate.execute(
                    CHECK_AND_RECORD,
                    keys(celular, ipAddress),
                    UUID.randomUUID().toString()
            );
            if (retryAfter == null || retryAfter < 0) {
                throw unavailable(new IllegalStateException("Redis returned an invalid verification limit result"));
            }
            if (retryAfter > 0) {
                throw new DomainException(
                        "RATE_LIMITED",
                        "Se alcanzó el límite de solicitudes de verificación. Inténtalo más tarde.",
                        Map.of("retryAfter", retryAfter)
                );
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private List<String> keys(String celular, String ipAddress) {
        return List.of(
                "auth:verification:phone:" + digest(celular),
                "auth:verification:ip:" + digest(ipAddress)
        );
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
        log.error("Redis unavailable while enforcing phone verification request limits", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible validar el límite de solicitudes de verificación en este momento."
        );
    }
}
