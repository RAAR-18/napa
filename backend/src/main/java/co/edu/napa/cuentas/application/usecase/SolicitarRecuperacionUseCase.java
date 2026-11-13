package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.SolicitarRecuperacionPort;
import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * La elegibilidad (cuenta activa, no administrador) y la respuesta uniforme las resuelve
 * SolicitarVerificacionUseCase, para que los límites y la respuesta no revelen qué números tienen cuenta.
 */
@Component
public class SolicitarRecuperacionUseCase implements SolicitarRecuperacionPort {
    private final SolicitarVerificacionPort solicitarVerificacion;

    public SolicitarRecuperacionUseCase(SolicitarVerificacionPort solicitarVerificacion) {
        this.solicitarVerificacion = Objects.requireNonNull(solicitarVerificacion, "solicitarVerificacion");
    }

    @Override
    public void solicitar(String celular, String ipAddress) {
        solicitarVerificacion.solicitar(
                celular, PropositoVerificacion.RECUPERACION, CanalVerificacion.WHATSAPP, ipAddress
        );
    }
}