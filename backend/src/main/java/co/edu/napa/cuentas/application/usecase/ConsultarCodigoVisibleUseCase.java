package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarCodigoVisiblePort;
import co.edu.napa.cuentas.application.ports.out.CodigoVisiblePort;
import co.edu.napa.cuentas.application.ports.out.MensajeRepositoryPort;
import co.edu.napa.cuentas.domain.model.EstadoMensaje;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Solo el registro muestra el código: en la recuperación o el cambio de celular, mostrarlo
 * permitiría tomar una cuenta ajena con solo conocer su número.
 */
@Component
public class ConsultarCodigoVisibleUseCase implements ConsultarCodigoVisiblePort {
    private final MensajeRepositoryPort mensajes;
    private final Optional<CodigoVisiblePort> codigoVisible;

    public ConsultarCodigoVisibleUseCase(MensajeRepositoryPort mensajes, Optional<CodigoVisiblePort> codigoVisible) {
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.codigoVisible = Objects.requireNonNull(codigoVisible, "codigoVisible");
    }

    @Override
    public Optional<String> consultar(UUID mensajeId) {
        if (mensajeId == null || codigoVisible.isEmpty()) {
            return Optional.empty();
        }
        return mensajes.findById(mensajeId)
                .filter(mensaje -> mensaje.proposito() == PropositoVerificacion.REGISTRO)
                .filter(mensaje -> mensaje.estado() == EstadoMensaje.ENVIADO)
                .filter(mensaje -> mensaje.proveedorId() != null)
                .flatMap(mensaje -> codigoVisible.get().codigoVigente(mensaje.proveedorId()));
    }
}
