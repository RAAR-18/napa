package co.edu.napa.domicilios.application.ports.in;

import java.time.Instant;
import java.util.UUID;

/** RF-19: el domiciliario acepta un domicilio disponible con la tarifa publicada. */
public interface AceptarDomicilioPort {
    DomicilioAceptado aceptar(UUID domiciliarioId, UUID domicilioId);

    record DomicilioAceptado(UUID id, String estado, long ganancia, boolean rutaDisponible, Instant updatedAt) {}
}
