package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!otp-console")
public class UnavailableVerificarCelularAdapter implements VerificarCelularPort {
    @Override
    public VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo) {
        throw unavailable();
    }

    @Override
    public EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal) {
        throw unavailable();
    }

    @Override
    public void solicitar(String celular, Enum<?> estado, String ipAddress) {
        throw unavailable();
    }

    private DomainException unavailable() {
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "La verificación del celular no está configurada. Inténtalo más tarde."
        );
    }
}
