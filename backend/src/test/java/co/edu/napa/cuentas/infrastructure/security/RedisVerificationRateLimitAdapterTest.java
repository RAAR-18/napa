package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisVerificationRateLimitAdapterTest {
    @Test
    void reportsRetryAfterWhenPhoneOrIpIsOverLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(Object[].class)
        )).thenReturn(60L);
        RedisVerificationRateLimitAdapter adapter = new RedisVerificationRateLimitAdapter(redisTemplate);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.checkAndRecord("+573001234567", "192.0.2.10")
        );

        assertEquals("RATE_LIMITED", exception.code());
        assertEquals(60L, exception.details().get("retryAfter"));
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<java.util.List<String>>argThat(keys ->
                    keys.size() == 2
                            && keys.stream().noneMatch(key ->
                                    key.contains("+573001234567") || key.contains("192.0.2.10"))
                ),
                any(Object[].class)
        );
    }

    @Test
    void failsClosedWhenRedisReturnsNoLimitDecision() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(Object[].class)
        )).thenReturn(null);
        RedisVerificationRateLimitAdapter adapter = new RedisVerificationRateLimitAdapter(redisTemplate);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.checkAndRecord("+573001234567", "192.0.2.10")
        );

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
    }

    @Test
    void failsExplicitlyWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(Object[].class)
        )).thenThrow(new RedisConnectionFailureException("connection refused"));
        RedisVerificationRateLimitAdapter adapter = new RedisVerificationRateLimitAdapter(redisTemplate);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.checkAndRecord("+573001234567", "192.0.2.10")
        );

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
    }
}
