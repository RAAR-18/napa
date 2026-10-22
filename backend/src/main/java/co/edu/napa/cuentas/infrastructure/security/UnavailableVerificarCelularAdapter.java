package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!otp-console")
public class UnavailableVerificarCelularAdapter implements VerificarCelularPort {
    @Override
    public void solicitar(String celular, EstadoVerificacion estado, String ipAddress) {
        throw unavailable();
    }

    @Override
    public VerificacionResultado confirmar(String celular, EstadoVerificacion estado, String codigo) {
        throw unavailable();
    }

    private DomainException unavailable() {
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "La verificación del celular no está configurada. Inténtalo más tarde."
        );
    }
}
