package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmsCorreoVerificacionAdapterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hash = mock(HashOperations.class);
    private final SmsReactorClient sms = mock(SmsReactorClient.class);
    private final BrevoCorreoClient correo = mock(BrevoCorreoClient.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SmsCorreoVerificacionAdapter adapter = new SmsCorreoVerificacionAdapter(
            redis, sms, correo, 25, 300, Clock.fixed(Instant.parse("2026-11-22T15:00:00Z"), ZoneOffset.UTC));

    SmsCorreoVerificacionAdapterTest() {
        when(redis.opsForHash()).thenReturn(hash);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
    }

    @Test
    void sendsTheCodeBySms() {
        adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.SMS);

        String codigo = codigoGuardado();
        verify(sms).enviar(eq("+573007080825"), eq("Napa: tu codigo de verificacion es " + codigo
                + ". Vence en 10 minutos. No lo compartas."));
        verifyNoInteractions(correo);
    }

    @Test
    void sendsTheCodeByEmail() {
        adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.CORREO, "ana@example.com");

        String codigo = codigoGuardado();
        verify(correo).enviarCodigo("ana@example.com", codigo);
        verifyNoInteractions(sms);
    }

    @Test
    void doesNotStoreACodeThatCouldNotBeDelivered() {
        doThrow(new EnvioFallidoException("HTTP 400")).when(sms).enviar(anyString(), anyString());

        DomainException exception = assertThrows(DomainException.class, () ->
                adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.SMS));

        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
        verifyNoInteractions(hash);
    }

    @Test
    void stopsSendingSmsWhenTheDailyQuotaIsUsedUp() {
        when(values.increment("auth:verification:codigo:cupo:sms:2026-11-22")).thenReturn(26L);

        DomainException exception = assertThrows(DomainException.class, () ->
                adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.SMS));

        assertTrue(exception.getMessage().contains("Pide el código por correo"));
        verifyNoInteractions(sms);
        verify(values).decrement("auth:verification:codigo:cupo:sms:2026-11-22");
    }

    @Test
    void returnsTheQuotaWhenTheSendFails() {
        doThrow(new EnvioFallidoException("HTTP 500")).when(correo).enviarCodigo(anyString(), anyString());

        assertThrows(DomainException.class, () -> adapter.iniciar(
                "+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.CORREO, "ana@example.com"));

        verify(values).decrement("auth:verification:codigo:cupo:correo:2026-11-22");
    }

    @SuppressWarnings("unchecked")
    private String codigoGuardado() {
        ArgumentCaptor<Map<Object, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hash).putAll(anyString(), captor.capture());
        return captor.getValue().get("code").toString();
    }
}
