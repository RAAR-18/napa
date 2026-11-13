package co.edu.napa.domicilios.application.ports.out;

import co.edu.napa.shared.domain.geo.Ubicacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la lista de disponibles (RF-16). {@code distanciaAlOrigenMetros} es nula si no se conoce la posición del
 * domiciliario.
 */
public record DomicilioDisponibleProjection(
        UUID id,
        long tarifa,
        Instant createdAt,
        Ubicacion origen,
        Ubicacion destino,
        long distanciaMetros,
        Long distanciaAlOrigenMetros
) {}
