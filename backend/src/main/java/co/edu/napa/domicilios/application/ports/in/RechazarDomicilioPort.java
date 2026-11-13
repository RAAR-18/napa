package co.edu.napa.domicilios.application.ports.in;

import java.util.UUID;

/** RF-17: rechazo definitivo y personal de un domicilio disponible. */
public interface RechazarDomicilioPort {
    void rechazar(UUID domiciliarioId, UUID domicilioId);
}
