package co.edu.napa.domicilios.application.ports.in;

import java.util.UUID;

/** RF-25: el cliente ve el código que debe dictarle al domiciliario. */
public interface ConsultarCodigoConfirmacionPort {
    CodigoConfirmacion consultarCodigo(UUID compradorId, UUID domicilioId);

    record CodigoConfirmacion(UUID domicilioId, String estado, String codigo) {}
}
