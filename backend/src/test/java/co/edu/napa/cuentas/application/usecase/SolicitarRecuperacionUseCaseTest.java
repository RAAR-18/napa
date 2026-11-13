package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SolicitarRecuperacionUseCaseTest {
    @Mock private SolicitarVerificacionPort solicitarVerificacion;

    private SolicitarRecuperacionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SolicitarRecuperacionUseCase(solicitarVerificacion);
    }

    @Test
    void delegatesToTheVerificationUseCaseWithTheRecoveryPurpose() {
        useCase.solicitar("300 123 4567", "127.0.0.1");

        verify(solicitarVerificacion).solicitar(
                "300 123 4567", PropositoVerificacion.RECUPERACION, CanalVerificacion.WHATSAPP, "127.0.0.1"
        );
    }
}