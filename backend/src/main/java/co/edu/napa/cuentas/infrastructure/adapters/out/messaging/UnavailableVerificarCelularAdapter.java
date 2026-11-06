package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Proveedor por defecto mientras no haya Twilio ni adaptador de consola: todo responde 503. */
@Component
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "unavailable", matchIfMissing = true)
public class UnavailableVerificarCelularAdapter implements VerificarCelularPort {
    @Override
    public EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal) {
        throw unavailable();
    }

    @Override
    public VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo) {
        throw unavailable();
    }

    private DomainException unavailable() {
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "La verificación del celular no está configurada. Inténtalo más tarde."
        );
    }
}