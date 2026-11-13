package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarMensajeVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.MensajeRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class ConsultarMensajeVerificacionUseCase implements ConsultarMensajeVerificacionPort {
    private final MensajeRepositoryPort mensajes;

    public ConsultarMensajeVerificacionUseCase(MensajeRepositoryPort mensajes) {
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
    }

    @Override
    public MensajeVista consultar(UUID mensajeId) {
        return mensajes.findById(mensajeId)
                .map(mensaje -> new MensajeVista(
                        mensaje.id(), mensaje.canal(), mensaje.estado(), mensaje.smsDisponible()
                ))
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No encontramos ese envío."));
    }
}