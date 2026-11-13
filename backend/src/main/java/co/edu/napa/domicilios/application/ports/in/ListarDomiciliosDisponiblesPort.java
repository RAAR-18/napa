package co.edu.napa.domicilios.application.ports.in;

import co.edu.napa.shared.domain.geo.GeoPoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-16: domicilios disponibles para un domiciliario (sin los que él rechazó). */
public interface ListarDomiciliosDisponiblesPort {
    /** {@code posicion} es obligatoria: solo se listan los domicilios con origen dentro del radio. */
    Pagina listar(UUID domiciliarioId, Orden orden, GeoPoint posicion, int page, int size);

    enum Orden { RECIENTES, CERCANIA, GANANCIA }

    record Pagina(List<Item> content, int page, int size, long totalElements, long radioMetros) {
        public Pagina {
            content = List.copyOf(content);
        }
    }

    /** El destino va aproximado: la dirección exacta solo la ve el domiciliario asignado. */
    record Item(
            UUID id,
            long ganancia,
            long distanciaMetros,
            Long distanciaAlOrigenMetros,
            Instant createdAt,
            PuntoDomicilio origen,
            PuntoDomicilio destino
    ) {}
}
