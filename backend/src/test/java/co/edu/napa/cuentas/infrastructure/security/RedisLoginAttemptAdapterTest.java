package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisLoginAttemptAdapterTest {
    @Test
    void blocksTheFifthFailedAttemptAndIncludesRetryTime() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(900L);
        RedisLoginAttemptAdapter adapter = new RedisLoginAttemptAdapter(redisTemplate);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.recordFailure("ana@example.com", "127.0.0.1")
        );

        assertEquals("RATE_LIMITED", exception.code());
        assertEquals(900L, exception.details().get("retryAfter"));
    }

    @Test
    void permitsLoginWhenThereIsNoActiveLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(0L);
        RedisLoginAttemptAdapter adapter = new RedisLoginAttemptAdapter(redisTemplate);

        adapter.ensureAllowed("ana@example.com", "127.0.0.1");
    }
}
