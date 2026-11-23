package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.CodigoVisiblePort;
import co.edu.napa.cuentas.application.ports.out.MensajeRepositoryPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoMensaje;
import co.edu.napa.cuentas.domain.model.Mensaje;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarCodigoVisibleUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    @Mock private MensajeRepositoryPort mensajes;
    @Mock private CodigoVisiblePort codigoVisible;

    @Test
    void showsTheCodeOfARegistrationMessage() {
        Mensaje mensaje = mensaje(PropositoVerificacion.REGISTRO);
        when(mensajes.findById(mensaje.id())).thenReturn(Optional.of(mensaje));
        when(codigoVisible.codigoVigente("imagen-1")).thenReturn(Optional.of("481516"));

        Optional<String> codigo = new ConsultarCodigoVisibleUseCase(mensajes, Optional.of(codigoVisible))
                .consultar(mensaje.id());

        assertEquals(Optional.of("481516"), codigo);
    }

    @Test
    void neverShowsRecoveryOrPhoneChangeCodes() {
        for (PropositoVerificacion proposito : new PropositoVerificacion[]{
                PropositoVerificacion.RECUPERACION, PropositoVerificacion.CAMBIO}) {
            Mensaje mensaje = mensaje(proposito);
            when(mensajes.findById(mensaje.id())).thenReturn(Optional.of(mensaje));

            assertTrue(new ConsultarCodigoVisibleUseCase(mensajes, Optional.of(codigoVisible))
                    .consultar(mensaje.id()).isEmpty());
        }
        verifyNoInteractions(codigoVisible);
    }

    @Test
    void showsNothingWhenTheProviderCannotDisplayCodes() {
        assertTrue(new ConsultarCodigoVisibleUseCase(mensajes, Optional.empty())
                .consultar(UUID.randomUUID()).isEmpty());
        verifyNoInteractions(mensajes);
    }

    private static Mensaje mensaje(PropositoVerificacion proposito) {
        return Mensaje.nuevo(
                CanalVerificacion.WHATSAPP, proposito, "+573001234567", EstadoMensaje.ENVIADO, "imagen-1", NOW
        );
    }
}
