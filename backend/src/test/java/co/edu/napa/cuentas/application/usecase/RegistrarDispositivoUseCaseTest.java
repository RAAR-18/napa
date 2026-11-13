package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.DispositivoTokenRepositoryPort;
import co.edu.napa.cuentas.domain.model.DispositivoToken;
import co.edu.napa.cuentas.domain.model.PlataformaDispositivo;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrarDispositivoUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");
    private static final UUID USUARIO = UUID.randomUUID();

    private DispositivoTokenRepositoryPort dispositivos;
    private RegistrarDispositivoUseCase useCase;

    @BeforeEach
    void setUp() {
        dispositivos = mock(DispositivoTokenRepositoryPort.class);
        useCase = new RegistrarDispositivoUseCase(dispositivos, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registraUnTokenNuevoYLoQuitaDeOtrasCuentas() {
        when(dispositivos.findByUsuarioIdAndToken(USUARIO, "fcm-token")).thenReturn(Optional.empty());

        useCase.registrar(USUARIO, " fcm-token ", "ANDROID");

        verify(dispositivos).deleteByTokenForOtherUsers("fcm-token", USUARIO);
        ArgumentCaptor<DispositivoToken> guardado = ArgumentCaptor.forClass(DispositivoToken.class);
        verify(dispositivos).save(guardado.capture());
        assertEquals("fcm-token", guardado.getValue().token());
        assertEquals(PlataformaDispositivo.ANDROID, guardado.getValue().plataforma());
        assertEquals(NOW, guardado.getValue().ultimaConexionAt());
    }

    @Test
    void repetirElRegistroSoloRenuevaLaUltimaConexion() {
        Instant antes = NOW.minusSeconds(86_400);
        DispositivoToken actual = new DispositivoToken(UUID.randomUUID(), USUARIO, "fcm-token",
                PlataformaDispositivo.ANDROID, antes, antes, antes);
        when(dispositivos.findByUsuarioIdAndToken(USUARIO, "fcm-token")).thenReturn(Optional.of(actual));

        useCase.registrar(USUARIO, "fcm-token", "IOS");

        ArgumentCaptor<DispositivoToken> guardado = ArgumentCaptor.forClass(DispositivoToken.class);
        verify(dispositivos).save(guardado.capture());
        assertEquals(actual.id(), guardado.getValue().id());
        assertEquals(antes, guardado.getValue().createdAt());
        assertEquals(NOW, guardado.getValue().ultimaConexionAt());
        assertEquals(PlataformaDispositivo.IOS, guardado.getValue().plataforma());
    }

    @Test
    void validaElTokenYLaPlataforma() {
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.registrar(USUARIO, " ", "ANDROID")).code());
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.registrar(USUARIO, "fcm-token", "WEB")).code());
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.registrar(USUARIO, "fcm-token", null)).code());
        verify(dispositivos, never()).save(any());
    }

    @Test
    void darDeBajaBorraSoloElTokenDeEsaCuenta() {
        useCase.darDeBaja(USUARIO, "fcm-token");

        verify(dispositivos).deleteByUsuarioIdAndToken(USUARIO, "fcm-token");
    }
}
