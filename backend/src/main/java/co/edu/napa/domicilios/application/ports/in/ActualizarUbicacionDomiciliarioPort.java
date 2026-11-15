package co.edu.napa.domicilios.application.ports.in;

import java.util.UUID;

/** RF-26: la app del domiciliario envía su posición cada 15 s mientras el domicilio está EN_CAMINO. */
public interface ActualizarUbicacionDomiciliarioPort {
    void actualizarUbicacion(UUID domiciliarioId, UUID domicilioId, double latitud, double longitud);
}
