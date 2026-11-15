package co.edu.napa.domicilios.application.ports.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * RF-26 FR-003: última posición del domiciliario en camino. Es efímera (Redis, vence a los 2 minutos) y nunca se
 * guarda en la base (RNF04).
 */
public interface UbicacionDomiciliarioPort {
    /** Devuelve {@code false} si ya hubo un envío en los últimos 5 segundos. */
    boolean guardar(UUID domicilioId, double latitud, double longitud, Instant ahora);

    Optional<Posicion> consultar(UUID domicilioId);

    void borrar(UUID domicilioId);

    record Posicion(double latitud, double longitud, Instant actualizadaAt) {}

    /** Para los contextos que no guardan posiciones (pruebas). */
    UbicacionDomiciliarioPort NINGUNA = new UbicacionDomiciliarioPort() {
        @Override
        public boolean guardar(UUID domicilioId, double latitud, double longitud, Instant ahora) {
            return true;
        }

        @Override
        public Optional<Posicion> consultar(UUID domicilioId) {
            return Optional.empty();
        }

        @Override
        public void borrar(UUID domicilioId) {
        }
    };
}
