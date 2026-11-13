package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ActivarCuentaPort;
import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.ConfirmarVerificacionPort;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmarRegistroUseCaseTest {
    @Mock private ConfirmarVerificacionPort confirmarVerificacion;
    @Mock private ActivarCuentaPort activarCuenta;

    private ConfirmarRegistroUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmarRegistroUseCase(confirmarVerificacion, activarCuenta);
    }

    @Test
    void checksTheCodeBeforeActivatingTheAccount() {
        AuthTokens tokens = new AuthTokens(
                "access", "refresh", 900, new AuthTokens.UsuarioResumen(UUID.randomUUID(), "Ana", "CLIENTE")
        );
        when(activarCuenta.activar("+573001234567", "device-1")).thenReturn(tokens);

        AuthTokens result = useCase.confirmar("+573001234567", "123456", "device-1");

        assertSame(tokens, result);
        InOrder orden = inOrder(confirmarVerificacion, activarCuenta);
        orden.verify(confirmarVerificacion)
                .confirmar("+573001234567", PropositoVerificacion.REGISTRO, "123456");
        orden.verify(activarCuenta).activar("+573001234567", "device-1");
    }

    @Test
    void doesNotActivateWhenTheCodeIsInvalid() {
        doThrow(new DomainException("OTP_INVALID", "Código incorrecto"))
                .when(confirmarVerificacion)
                .confirmar("+573001234567", PropositoVerificacion.REGISTRO, "000000");

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.confirmar("+573001234567", "000000", "device-1")
        );

        assertEquals("OTP_INVALID", exception.code());
        verify(activarCuenta, never()).activar(anyString(), anyString());
    }
}