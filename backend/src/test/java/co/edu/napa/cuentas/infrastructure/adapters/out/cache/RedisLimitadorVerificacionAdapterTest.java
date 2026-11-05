package co.edu.napa.cuentas.infrastructure.adapters.out.cache;

import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLimitadorVerificacionAdapterTest {
    private static final String CELULAR = "+573001234567";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisLimitadorVerificacionAdapter adapter = new RedisLimitadorVerificacionAdapter(redisTemplate);

    @Test
    void rejectsASendWhenALimitWasReachedAndReportsTheWait() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)
        )).thenReturn(42L);

        DomainException exception = assertThrows(
                DomainException.class, () -> adapter.registrarEnvio(CELULAR, "127.0.0.1")
        );

        assertEquals("RATE_LIMITED", exception.code());
        assertEquals(42L, exception.details().get("retryAfter"));
    }

    @Test
    void permitsASendWhenNoLimitWasReached() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(Object[].class)
        )).thenReturn(0L);

        assertDoesNotThrow(() -> adapter.registrarEnvio(CELULAR, "127.0.0.1"));
    }

    @Test
    void reportsTheRemainingAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("2");

        assertEquals(3, adapter.intentosRestantes(CELULAR, PropositoVerificacion.REGISTRO));
    }

    @Test
    void startsTheExpiryOnTheFirstFailedAttemptAndNeverGoesBelowZero() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);

        assertEquals(4, adapter.registrarIntentoFallido(CELULAR, PropositoVerificacion.REGISTRO));
        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(10)));

        when(values.increment(anyString())).thenReturn(7L);
        assertEquals(0, adapter.registrarIntentoFallido(CELULAR, PropositoVerificacion.REGISTRO));
    }
}