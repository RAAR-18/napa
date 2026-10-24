package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisSessionVersionAdapter implements SessionVersionPort {
    private static final Logger log = LoggerFactory.getLogger(RedisSessionVersionAdapter.class);
    private final StringRedisTemplate redisTemplate;

    public RedisSessionVersionAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(UUID sessionId, long version, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(sessionId), Long.toString(version), ttl);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public boolean matches(UUID sessionId, long version) {
        try {
            String storedVersion = redisTemplate.opsForValue().get(key(sessionId));
            return storedVersion != null && storedVersion.equals(Long.toString(version));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void revoke(UUID sessionId, long nextVersion, Duration ttl) {
        set(sessionId, nextVersion, ttl);
    }

    private String key(UUID sessionId) {
        return "sv:" + sessionId;
    }

    private DomainException unavailable(DataAccessException exception) {
        log.error("Redis unavailable while validating a session version", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible validar la sesión en este momento."
        );
    }
}
