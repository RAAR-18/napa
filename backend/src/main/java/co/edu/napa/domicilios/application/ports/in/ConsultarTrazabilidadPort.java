package co.edu.napa.domicilios.application.ports.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-26: estado y cronología del domicilio para el cliente y el vendedor del pedido. */
public interface ConsultarTrazabilidadPort {
    Trazabilidad consultarTrazabilidad(UUID usuarioId, UUID domicilioId);

    /**
     * Sin código ni autores de los eventos. {@code ubicacionDomiciliario} solo llega al cliente con el domicilio
     * EN_CAMINO y es nula si el domiciliario no envió su posición en los últimos 2 minutos.
     */
    record Trazabilidad(
            UUID domicilioId,
            String estado,
            String motivoCancelacion,
            Instant updatedAt,
            List<Evento> eventos,
            UbicacionDomiciliario ubicacionDomiciliario
    ) {
        public Trazabilidad {
            eventos = List.copyOf(eventos);
        }
    }

    record Evento(String estado, Instant ocurridoAt) {}

    record UbicacionDomiciliario(double latitud, double longitud, Instant actualizadaAt) {}
}
