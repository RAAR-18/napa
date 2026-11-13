package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarMensajeVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.MensajeRepositoryPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoMensaje;
import co.edu.napa.cuentas.domain.model.Mensaje;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarMensajeVerificacionUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private MensajeRepositoryPort mensajes;

    private ConsultarMensajeVerificacionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConsultarMensajeVerificacionUseCase(mensajes);
    }

    @Test
    void offersSmsWhenTheWhatsappSendFailed() {
        Mensaje fallido = Mensaje.nuevo(
                CanalVerificacion.WHATSAPP, PropositoVerificacion.REGISTRO, "+573001234567",
                EstadoMensaje.FALLIDO, null, NOW
        );
        when(mensajes.findById(fallido.id())).thenReturn(Optional.of(fallido));

        ConsultarMensajeVerificacionPort.MensajeVista vista = useCase.consultar(fallido.id());

        assertEquals(EstadoMensaje.FALLIDO, vista.estado());
        assertTrue(vista.smsDisponible());
    }

    @Test
    void doesNotOfferSmsWhenTheMessageWasSent() {
        Mensaje enviado = Mensaje.nuevo(
                CanalVerificacion.WHATSAPP, PropositoVerificacion.REGISTRO, "+573001234567",
                EstadoMensaje.ENVIADO, "sid-1", NOW
        );
        when(mensajes.findById(enviado.id())).thenReturn(Optional.of(enviado));

        assertFalse(useCase.consultar(enviado.id()).smsDisponible());
    }

    @Test
    void reportsNotFoundForAnUnknownMessage() {
        UUID desconocido = UUID.randomUUID();
        when(mensajes.findById(desconocido)).thenReturn(Optional.empty());

        DomainException exception = assertThrows(DomainException.class, () -> useCase.consultar(desconocido));

        assertEquals("NOT_FOUND", exception.code());
    }
}