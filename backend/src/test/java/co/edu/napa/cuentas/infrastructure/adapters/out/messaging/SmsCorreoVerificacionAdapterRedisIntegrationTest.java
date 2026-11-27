package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class SmsCorreoVerificacionAdapterRedisIntegrationTest {
    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"), ZoneOffset.UTC);
    private final SmsGateClient sms = mock(SmsGateClient.class);
    private final BrevoCorreoClient correo = mock(BrevoCorreoClient.class);
    private SmsCorreoVerificacionAdapter adapter() {
        return new SmsCorreoVerificacionAdapter(template, sms, correo, 25, 300, CLOCK);
    }
    @BeforeAll static void conectar() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(redis.getHost(), redis.getFirstMappedPort()));
        factory.afterPropertiesSet(); factory.start();
        template = new StringRedisTemplate(factory); template.afterPropertiesSet();
    }
    @AfterAll static void cerrar() { if (factory != null) factory.destroy(); }

    @Test void storesAnExpiringCodeAndConsumesItOnlyOnce() throws Exception {
        String celular = "+573001111111";
        var adapter = adapter();
        adapter.iniciar(celular, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS);
        String codigo = codigo(celular, 1);
        Long ttl = template.getExpire(key(celular));
        assertNotNull(ttl); assertTrue(ttl > 0 && ttl <= 600);
        assertEquals(VerificacionResultado.Estado.APROBADA, adapter.confirmar(celular, PropositoVerificacion.REGISTRO, codigo));
        assertEquals(VerificacionResultado.Estado.VENCIDA, adapter.confirmar(celular, PropositoVerificacion.REGISTRO, codigo));
    }

    @Test void invalidatesAfterFiveFailures() {
        String celular = "+573001111112";
        var adapter = adapter();
        adapter.iniciar(celular, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS);
        String correcto = codigo(celular, 1);
        String incorrecto = correcto.equals("000000") ? "999999" : "000000";
        for (int i = 0; i < 4; i++) assertEquals(VerificacionResultado.Estado.INCORRECTA,
                adapter.confirmar(celular, PropositoVerificacion.REGISTRO, incorrecto));
        assertEquals(VerificacionResultado.Estado.AGOTADA, adapter.confirmar(celular, PropositoVerificacion.REGISTRO, incorrecto));
        assertEquals(VerificacionResultado.Estado.VENCIDA, adapter.confirmar(celular, PropositoVerificacion.REGISTRO, correcto));
    }

    @Test void failedResendPreservesThePreviousCode() {
        String celular = "+573001111113";
        var adapter = adapter();
        adapter.iniciar(celular, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS);
        String anterior = codigo(celular, 1);
        doThrow(new EnvioFallidoException("Proveedor no disponible")).when(sms).enviar(anyString(), anyString());
        assertThrows(DomainException.class, () -> adapter.iniciar(celular, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS));
        assertEquals(VerificacionResultado.Estado.APROBADA, adapter.confirmar(celular, PropositoVerificacion.REGISTRO, anterior));
    }

    @SuppressWarnings("unchecked")
    @Test void confirmationCannotDeleteAChallengeResentAfterReadingTheOldCode() throws Exception {
        String celular = "+573001111114";
        StringRedisTemplate delegado = spy(template);
        HashOperations<String, Object, Object> hashes = spy(template.opsForHash());
        doReturn(hashes).when(delegado).opsForHash();
        var adapter = new SmsCorreoVerificacionAdapter(delegado, sms, correo, 25, 300, CLOCK);
        adapter.iniciar(celular, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS);
        String anterior = codigo(celular, 1);
        AtomicBoolean reenviar = new AtomicBoolean(true);
        doAnswer(invocacion -> {
            Object lectura = invocacion.callRealMethod();
            if (reenviar.getAndSet(false)) adapter.iniciar(celular, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS);
            return lectura;
        }).when(hashes).multiGet(eq(key(celular)), eq(List.of("code", "generation")));
        assertEquals(VerificacionResultado.Estado.VENCIDA, adapter.confirmar(celular, PropositoVerificacion.REGISTRO, anterior));
        assertTrue(Boolean.TRUE.equals(template.hasKey(key(celular))));
        assertEquals(VerificacionResultado.Estado.APROBADA, adapter.confirmar(celular, PropositoVerificacion.REGISTRO, codigo(celular, 2)));
    }

    @Test void acceptsAnUnexpiredLegacyChallenge() throws Exception {
        String celular = "+573001111115";
        template.opsForHash().putAll(key(celular), Map.of("code", "123456", "attempts", "0"));
        template.expire(key(celular), Duration.ofMinutes(10));
        assertEquals(VerificacionResultado.Estado.APROBADA, adapter().confirmar(celular, PropositoVerificacion.REGISTRO, "123456"));
    }

    @Test void rejectsAndRemovesAChallengeWithoutExpiration() throws Exception {
        String celular = "+573001111116";
        template.opsForHash().putAll(key(celular), Map.of("code", "123456", "attempts", "0"));
        assertEquals(VerificacionResultado.Estado.VENCIDA, adapter().confirmar(celular, PropositoVerificacion.REGISTRO, "123456"));
        assertFalse(Boolean.TRUE.equals(template.hasKey(key(celular))));
    }

    @Test void simultaneousSendsRespectTheLastDailySlot() throws Exception {
        var adapter = new SmsCorreoVerificacionAdapter(template, sms, correo, 1, 300,
                Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> enviar(adapter, "+573001111117", start));
            var b = executor.submit(() -> enviar(adapter, "+573001111118", start));
            start.countDown();
            assertTrue(a.get(10, TimeUnit.SECONDS) ^ b.get(10, TimeUnit.SECONDS));
            verify(sms, times(1)).enviar(anyString(), anyString());
            assertEquals("1", template.opsForValue().get("auth:verification:codigo:cupo:sms:2026-11-25"));
        } finally { executor.shutdownNow(); }
    }

    private boolean enviar(SmsCorreoVerificacionAdapter adapter, String celular, CountDownLatch start) throws InterruptedException {
        start.await();
        try { adapter.iniciar(celular, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS); return true; }
        catch (DomainException error) { assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", error.code()); return false; }
    }
    private String codigo(String celular, int cantidad) {
        ArgumentCaptor<String> mensajes = ArgumentCaptor.forClass(String.class);
        verify(sms, times(cantidad)).enviar(eq(celular), mensajes.capture());
        var match = Pattern.compile("\\b\\d{6}\\b").matcher(mensajes.getAllValues().getLast());
        assertTrue(match.find()); return match.group();
    }
    private String key(String celular) throws Exception {
        return "auth:verification:codigo:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((celular + ":REGISTRO").getBytes(StandardCharsets.UTF_8)));
    }
}
