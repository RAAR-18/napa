package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsolaVerificacionAdapterTest {
    private static final String CELULAR = "+573001234567";

    private final AjustableClock clock = new AjustableClock(Instant.parse("2026-10-20T12:00:00Z"));
    private final ConsolaVerificacionAdapter adapter = new ConsolaVerificacionAdapter(clock, "123456");

    @Test
    void approvesTheConfiguredCodeOnlyOnce() {
        adapter.iniciar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP);

        assertEquals(VerificacionResultado.Estado.APROBADA,
                adapter.confirmar(CELULAR, PropositoVerificacion.REGISTRO, "123456"));
        assertEquals(VerificacionResultado.Estado.VENCIDA,
                adapter.confirmar(CELULAR, PropositoVerificacion.REGISTRO, "123456"));
    }

    @Test
    void rejectsAWrongCodeWithoutConsumingTheVerification() {
        adapter.iniciar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP);

        assertEquals(VerificacionResultado.Estado.INCORRECTA,
                adapter.confirmar(CELULAR, PropositoVerificacion.REGISTRO, "000000"));
        assertEquals(VerificacionResultado.Estado.APROBADA,
                adapter.confirmar(CELULAR, PropositoVerificacion.REGISTRO, "123456"));
    }

    @Test
    void expiresTheCodeAfterTenMinutes() {
        adapter.iniciar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP);
        clock.avanzar(Duration.ofMinutes(11));

        assertEquals(VerificacionResultado.Estado.VENCIDA,
                adapter.confirmar(CELULAR, PropositoVerificacion.REGISTRO, "123456"));
    }

    @Test
    void doesNotApproveWithoutAPendingVerificationOrForAnotherPurpose() {
        assertEquals(VerificacionResultado.Estado.VENCIDA,
                adapter.confirmar(CELULAR, PropositoVerificacion.REGISTRO, "123456"));

        adapter.iniciar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP);
        assertEquals(VerificacionResultado.Estado.VENCIDA,
                adapter.confirmar(CELULAR, PropositoVerificacion.RECUPERACION, "123456"));
    }

    @Test
    void returnsAProviderIdWithoutExposingTheCode() {
        String proveedorId = adapter.iniciar(CELULAR, PropositoVerificacion.REGISTRO, CanalVerificacion.SMS)
                .proveedorId();

        assertTrue(proveedorId.startsWith("console-"));
        assertTrue(!proveedorId.contains("123456"));
    }

    private static final class AjustableClock extends Clock {
        private Instant ahora;

        private AjustableClock(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}