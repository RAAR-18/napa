package co.edu.napa.domicilios.domain.model;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomicilioTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    @Test
    void nacesDisponibleSinDomiciliarioConLaTarifaDelPedido() {
        Domicilio domicilio = publicado();

        assertEquals(EstadoDomicilio.DISPONIBLE, domicilio.estado());
        assertNull(domicilio.domiciliarioId());
        assertNull(domicilio.motivoCancelacion());
        assertEquals(4_500, domicilio.tarifa());
        assertEquals(NOW, domicilio.createdAt());
    }

    @Test
    void rechazaTarifaNoPositiva() {
        DomainException error = assertThrows(DomainException.class, () -> Domicilio.publicar(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 0, UUID.randomUUID(), UUID.randomUUID(), NOW));
        assertEquals("VALIDATION_ERROR", error.code());
    }

    @Test
    void avisaUnaSolaVez() {
        Domicilio avisado = publicado().marcarAvisoSinAsignar(NOW.plusSeconds(1_800));

        assertEquals(NOW.plusSeconds(1_800), avisado.avisoSinAsignarAt());
        assertEquals(EstadoDomicilio.DISPONIBLE, avisado.estado());
        assertThrows(DomainException.class, () -> avisado.marcarAvisoSinAsignar(NOW.plusSeconds(1_900)));
    }

    @Test
    void cancelaPorFaltaDeAsignacionConMotivo() {
        Domicilio cancelado = publicado().cancelarPorFaltaDeAsignacion(NOW.plusSeconds(7_200));

        assertEquals(EstadoDomicilio.CANCELADO, cancelado.estado());
        assertEquals(MotivoCancelacion.SIN_ASIGNAR, cancelado.motivoCancelacion());
        assertEquals(NOW.plusSeconds(7_200), cancelado.updatedAt());
        assertEquals(false, cancelado.activo());
    }

    @Test
    void soloSeCancelaUnDomicilioDisponible() {
        Domicilio cancelado = publicado().cancelarPorFaltaDeAsignacion(NOW);

        DomainException error = assertThrows(DomainException.class,
                () -> cancelado.cancelarPorFaltaDeAsignacion(NOW));
        assertEquals("NOT_AVAILABLE", error.code());
        assertEquals("CANCELADO", error.details().get("estadoActual"));
    }

    @Test
    void elEventoDeTrazabilidadTomaElEstadoYLaFechaDelCambio() {
        UUID vendedor = UUID.randomUUID();
        Domicilio domicilio = publicado();

        EventoTrazabilidad evento = EventoTrazabilidad.de(domicilio, vendedor);

        assertNotNull(evento.id());
        assertEquals(domicilio.id(), evento.domicilioId());
        assertEquals(EstadoDomicilio.DISPONIBLE, evento.estado());
        assertEquals(vendedor, evento.usuarioId());
        assertEquals(NOW, evento.ocurridoAt());
    }

    @Test
    void asignaConCodigoYSoloSiEstaDisponible() {
        UUID domiciliario = UUID.randomUUID();
        Domicilio asignado = publicado().asignar(domiciliario, "012345", NOW.plusSeconds(60));

        assertEquals(EstadoDomicilio.ASIGNADO, asignado.estado());
        assertEquals("012345", asignado.codigoConfirmacion());
        assertEquals(NOW.plusSeconds(60), asignado.asignadoAt());
        assertEquals(true, asignado.asignadoA(domiciliario));
        assertEquals("NOT_AVAILABLE", assertThrows(DomainException.class,
                () -> asignado.asignar(UUID.randomUUID(), "111111", NOW)).code());
        assertEquals("NOT_AVAILABLE", assertThrows(DomainException.class,
                () -> asignado.cancelarPorFaltaDeAsignacion(NOW)).code());
    }

    @Test
    void elCodigoDebeTenerSeisDigitos() {
        assertThrows(DomainException.class, () -> publicado().asignar(UUID.randomUUID(), "12345", NOW));
    }

    private static Domicilio publicado() {
        return Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4_500,
                UUID.randomUUID(), UUID.randomUUID(), NOW);
    }
}
