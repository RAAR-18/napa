package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ActivarCuentaPort;
import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.ConfirmarRegistroPort;
import co.edu.napa.cuentas.application.ports.in.ConfirmarVerificacionPort;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.cuentas.domain.model.Sesion;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Une los dos pasos del registro: comprobar el código (RF-70) y activar la cuenta (RF-07). */
@Component
public class ConfirmarRegistroUseCase implements ConfirmarRegistroPort {
    private final ConfirmarVerificacionPort confirmarVerificacion;
    private final ActivarCuentaPort activarCuenta;

    public ConfirmarRegistroUseCase(
            ConfirmarVerificacionPort confirmarVerificacion,
            ActivarCuentaPort activarCuenta
    ) {
        this.confirmarVerificacion = Objects.requireNonNull(confirmarVerificacion, "confirmarVerificacion");
        this.activarCuenta = Objects.requireNonNull(activarCuenta, "activarCuenta");
    }

    @Override
    public AuthTokens confirmar(String celular, String codigo, String deviceId) {
        Sesion.validarDeviceId(deviceId);
        confirmarVerificacion.confirmar(celular, PropositoVerificacion.REGISTRO, codigo);
        return activarCuenta.activar(celular, deviceId);
    }
}