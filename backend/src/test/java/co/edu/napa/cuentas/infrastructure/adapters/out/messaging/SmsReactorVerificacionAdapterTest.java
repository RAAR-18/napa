package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmsReactorVerificacionAdapterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hash = mock(HashOperations.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SmsReactorClient client = mock(SmsReactorClient.class);

    SmsReactorVerificacionAdapterTest() {
        when(redis.opsForHash()).thenReturn(hash);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void sendsTheCodeBySmsAndKeepsItHidden() {
        new SmsReactorVerificacionAdapter(redis, client, true)
                .iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP);

        Map<Object, Object> guardado = guardado();
        assertEquals("0", guardado.get("visible"));
        verify(client).enviar(eq("+573007080825"),
                eq("Napa: tu codigo de verificacion es " + guardado.get("code")
                        + ". Vence en 10 minutos. No lo compartas."));
    }

    @Test
    void showsTheRegistrationCodeAsAnImageWhenTheSmsFails() {
        doThrow(new SmsReactorClient.EnvioFallidoException("HTTP 402")).when(client).enviar(anyString(), anyString());

        new SmsReactorVerificacionAdapter(redis, client, true)
                .iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP);

        assertEquals("1", guardado().get("visible"));
    }

    @Test
    void neverFallsBackToTheImageForPasswordRecovery() {
        doThrow(new SmsReactorClient.EnvioFallidoException("HTTP 402")).when(client).enviar(anyString(), anyString());
        SmsReactorVerificacionAdapter adapter = new SmsReactorVerificacionAdapter(redis, client, true);

        DomainException exception = assertThrows(DomainException.class, () -> adapter.iniciar(
                "+573007080825", PropositoVerificacion.RECUPERACION, CanalVerificacion.WHATSAPP));

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
        verifyNoInteractions(hash);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> guardado() {
        ArgumentCaptor<Map<Object, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hash).putAll(anyString(), captor.capture());
        return captor.getValue();
    }
}
