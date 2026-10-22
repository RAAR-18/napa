package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsoleVerificarCelularAdapterTest {
    @Test
    void requiresAConfiguredSixDigitCode() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsoleVerificarCelularAdapter(redisTemplate, "not-a-code")
        );
    }

    @Test
    void createsTenMinuteVerificationStateWithoutStoringTheCode() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        ConsoleVerificarCelularAdapter adapter = new ConsoleVerificarCelularAdapter(redisTemplate, "123456");

        adapter.solicitar("+573001234567", EstadoVerificacion.REGISTRO, "192.0.2.10");

        verify(values).set(anyString(), eq("0"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void doesNotAllowAValidCodeToBeUsedTwice() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.delete(anyString())).thenReturn(true, false);
        ConsoleVerificarCelularAdapter adapter = new ConsoleVerificarCelularAdapter(redisTemplate, "123456");

        VerificacionResultado first = adapter.confirmar(
                "+573001234567",
                EstadoVerificacion.REGISTRO,
                "123456"
        );
        VerificacionResultado replay = adapter.confirmar(
                "+573001234567",
                EstadoVerificacion.REGISTRO,
                "123456"
        );

        assertEquals(VerificacionResultado.Estado.APROBADA, first.estado());
        assertEquals(VerificacionResultado.Estado.VENCIDA, replay.estado());
    }
}
