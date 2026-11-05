package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConfirmarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.LimitadorVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ConfirmarVerificacionUseCase implements ConfirmarVerificacionPort {
    private final VerificarCelularPort verificarCelular;
    private final LimitadorVerificacionPort limitador;

    public ConfirmarVerificacionUseCase(VerificarCelularPort verificarCelular, LimitadorVerificacionPort limitador) {
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
        this.limitador = Objects.requireNonNull(limitador, "limitador");
    }

    @Override
    public void confirmar(String celularInput, PropositoVerificacion proposito, String codigo) {
        String celular = Celular.desde(celularInput).valor();
        if (limitador.intentosRestantes(celular, proposito) <= 0) {
            ResultadoOtp.exigirAprobado(new VerificacionResultado(VerificacionResultado.Estado.AGOTADA, 0));
        }
        VerificacionResultado.Estado estado = verificarCelular.confirmar(celular, proposito, codigo);
        if (estado == VerificacionResultado.Estado.APROBADA) {
            limitador.reiniciarIntentos(celular, proposito);
            return;
        }
        if (estado == VerificacionResultado.Estado.INCORRECTA) {
            int restantes = limitador.registrarIntentoFallido(celular, proposito);
            ResultadoOtp.exigirAprobado(new VerificacionResultado(
                    restantes > 0 ? VerificacionResultado.Estado.INCORRECTA : VerificacionResultado.Estado.AGOTADA,
                    restantes
            ));
        }
        ResultadoOtp.exigirAprobado(new VerificacionResultado(estado, 0));
    }
}