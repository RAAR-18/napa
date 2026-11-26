package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class SmsCorreoVerificacionAdapterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SmsGateClient sms = mock(SmsGateClient.class);
    private final BrevoCorreoClient correo = mock(BrevoCorreoClient.class);
    private final SmsCorreoVerificacionAdapter adapter = new SmsCorreoVerificacionAdapter(
            redis, sms, correo, 25, 300,
            Clock.fixed(Instant.parse("2026-11-22T15:00:00Z"), ZoneOffset.UTC));

    SmsCorreoVerificacionAdapterTest() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
    }

    @Test
    void sendsTheSameCodeThatIsStoredWithItsExpirationBySms() {
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
    void doesNotReplaceTheChallengeWhenDeliveryFails() {
        doThrow(new EnvioFallidoException("HTTP 400")).when(sms).enviar(anyString(), anyString());
        DomainException error = assertThrows(DomainException.class, () ->
                adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.SMS));
        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", error.code());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void stopsSendingWhenTheDailyQuotaIsUsedUpWithoutIncrementingIt() {
        when(redis.execute(any(RedisScript.class), eq(List.of("auth:verification:codigo:cupo:sms:2026-11-22")),
                eq("25"), eq("172800"))).thenReturn(0L);
        DomainException error = assertThrows(DomainException.class, () ->
                adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.SMS));
        assertTrue(error.getMessage().contains("Pide el código por correo"));
        verifyNoInteractions(sms);
        verify(redis, never()).execute(any(RedisScript.class), anyList());
    }

    @Test
    void returnsTheQuotaWhenTheSendFails() {
        doThrow(new EnvioFallidoException("HTTP 500")).when(correo).enviarCodigo(anyString(), anyString());
        assertThrows(DomainException.class, () -> adapter.iniciar(
                "+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.CORREO, "ana@example.com"));
        verify(redis).execute(any(RedisScript.class), eq(List.of("auth:verification:codigo:cupo:correo:2026-11-22")));
    }

    @Test
    void doesNotContactProvidersWithoutAConfirmedQuotaReservation() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(null);
        DomainException error = assertThrows(DomainException.class, () ->
                adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.SMS));
        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", error.code());
        verifyNoInteractions(sms, correo);
    }

    @Test
    void rejectsMissingEmailBeforeReservingQuota() {
        DomainException error = assertThrows(DomainException.class, () -> adapter.iniciar(
                "+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.CORREO, " "));
        assertEquals("VALIDATION_ERROR", error.code());
        verifyNoInteractions(redis, sms, correo);
    }

    @Test
    void doesNotReportSuccessIfStorageIsNotConfirmed() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(null);
        DomainException error = assertThrows(DomainException.class, () ->
                adapter.iniciar("+573007080825", PropositoVerificacion.REGISTRO, CanalVerificacion.SMS));
        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", error.code());
    }

    private String codigoGuardado() {
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(any(RedisScript.class), anyList(), codigo.capture(), eq("600"), anyString());
        return codigo.getValue();
    }
}
