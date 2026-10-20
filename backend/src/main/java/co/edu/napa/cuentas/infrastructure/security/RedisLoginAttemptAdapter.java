package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.LoginAttemptPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
public class RedisLoginAttemptAdapter implements LoginAttemptPort {
    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptAdapter.class);
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final RedisScript<Long> CHECK_LIMIT = new DefaultRedisScript<>(
            """
            local retry = 0
            for _, key in ipairs(KEYS) do
                local count = tonumber(redis.call('get', key) or '0')
                if count >= tonumber(ARGV[1]) then
                    local ttl = redis.call('ttl', key)
                    if ttl > retry then retry = ttl end
                end
            end
            return retry
            """,
            Long.class
    );
    private static final RedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            """
            local retry = 0
            for _, key in ipairs(KEYS) do
                local count = redis.call('incr', key)
                if count == 1 then redis.call('expire', key, tonumber(ARGV[1])) end
                if count >= tonumber(ARGV[2]) then
                    local ttl = redis.call('ttl', key)
                    if ttl > retry then retry = ttl end
                end
            end
            return retry
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void ensureAllowed(String identifier, String ipAddress) {
        try {
            Long retryAfter = redisTemplate.execute(
                    CHECK_LIMIT,
                    keys(identifier, ipAddress),
                    Integer.toString(MAX_FAILURES)
            );
            if (retryAfter != null && retryAfter > 0) {
                throw rateLimited(retryAfter);
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void recordFailure(String identifier, String ipAddress) {
        try {
            Long retryAfter = redisTemplate.execute(
                    RECORD_FAILURE,
                    keys(identifier, ipAddress),
                    Long.toString(WINDOW.toSeconds()),
                    Integer.toString(MAX_FAILURES)
            );
            if (retryAfter != null && retryAfter > 0) {
                throw rateLimited(retryAfter);
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void clearIdentifier(String identifier) {
        try {
            redisTemplate.delete("auth:login:identifier:" + digest(identifier));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private List<String> keys(String identifier, String ipAddress) {
        return List.of(
                "auth:login:identifier:" + digest(identifier),
                "auth:login:ip:" + digest(ipAddress)
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

    private DomainException rateLimited(long retryAfter) {
        return new DomainException(
                "RATE_LIMITED",
                "Se alcanzó el límite de intentos. Inténtalo más tarde.",
                java.util.Map.of("retryAfter", retryAfter)
        );
    }

    private DomainException unavailable(DataAccessException exception) {
        log.error("Redis unavailable while enforcing login attempt limits", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible validar el límite de intentos en este momento."
        );
    }
}
