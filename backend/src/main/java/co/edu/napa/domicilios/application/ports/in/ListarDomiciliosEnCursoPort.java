package co.edu.napa.domicilios.application.ports.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domicilios que el domiciliario tiene asignados, recogidos o en camino (máximo 3, RF-19 FR-006). */
public interface ListarDomiciliosEnCursoPort {
    List<Item> listar(UUID domiciliarioId);

    record Item(UUID id, String estado, long ganancia, Instant asignadoAt, PuntoDomicilio origen,
                PuntoDomicilio destino) {}
}
