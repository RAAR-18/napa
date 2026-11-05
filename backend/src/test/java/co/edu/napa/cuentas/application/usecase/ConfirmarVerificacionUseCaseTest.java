package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.out.LimitadorVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmarVerificacionUseCaseTest {
    private static final String CELULAR = "+573001234567";
    private static final PropositoVerificacion PROPOSITO = PropositoVerificacion.REGISTRO;

    @Mock private VerificarCelularPort verificarCelular;
    @Mock private LimitadorVerificacionPort limitador;

    private ConfirmarVerificacionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmarVerificacionUseCase(verificarCelular, limitador);
    }

    @Test
    void approvedCodeResetsTheAttemptCounter() {
        when(limitador.intentosRestantes(CELULAR, PROPOSITO)).thenReturn(5);
        when(verificarCelular.confirmar(CELULAR, PROPOSITO, "123456"))
                .thenReturn(VerificacionResultado.Estado.APROBADA);

        assertDoesNotThrow(() -> useCase.confirmar("300 123 4567", PROPOSITO, "123456"));

        verify(limitador).reiniciarIntentos(CELULAR, PROPOSITO);
    }

    @Test
    void wrongCodeReportsTheRemainingAttempts() {
        when(limitador.intentosRestantes(CELULAR, PROPOSITO)).thenReturn(5);
        when(verificarCelular.confirmar(CELULAR, PROPOSITO, "000000"))
                .thenReturn(VerificacionResultado.Estado.INCORRECTA);
        when(limitador.registrarIntentoFallido(CELULAR, PROPOSITO)).thenReturn(3);

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.confirmar(CELULAR, PROPOSITO, "000000")
        );

        assertEquals("OTP_INVALID", exception.code());
        assertEquals(3, exception.details().get("intentosRestantes"));
    }

    @Test
    void theFifthWrongCodeExhaustsTheAttempts() {
        when(limitador.intentosRestantes(CELULAR, PROPOSITO)).thenReturn(1);
        when(verificarCelular.confirmar(CELULAR, PROPOSITO, "000000"))
                .thenReturn(VerificacionResultado.Estado.INCORRECTA);
        when(limitador.registrarIntentoFallido(CELULAR, PROPOSITO)).thenReturn(0);

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.confirmar(CELULAR, PROPOSITO, "000000")
        );

        assertEquals("OTP_ATTEMPTS_EXHAUSTED", exception.code());
    }

    @Test
    void blocksWithoutCallingTheProviderWhenNoAttemptsAreLeft() {
        when(limitador.intentosRestantes(CELULAR, PROPOSITO)).thenReturn(0);

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.confirmar(CELULAR, PROPOSITO, "123456")
        );

        assertEquals("OTP_ATTEMPTS_EXHAUSTED", exception.code());
        verify(verificarCelular, never()).confirmar(anyString(), any(), anyString());
    }

    @Test
    void expiredCodeDoesNotCountAsAFailedAttempt() {
        when(limitador.intentosRestantes(CELULAR, PROPOSITO)).thenReturn(5);
        when(verificarCelular.confirmar(CELULAR, PROPOSITO, "123456"))
                .thenReturn(VerificacionResultado.Estado.VENCIDA);

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.confirmar(CELULAR, PROPOSITO, "123456")
        );

        assertEquals("OTP_EXPIRED", exception.code());
        verify(limitador, never()).registrarIntentoFallido(anyString(), any());
    }
}