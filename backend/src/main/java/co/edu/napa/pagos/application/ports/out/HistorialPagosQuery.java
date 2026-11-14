package co.edu.napa.pagos.application.ports.out;

import co.edu.napa.pagos.domain.model.EstadoPago;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.TipoPago;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros del historial (RF-40 y RF-41). El filtro por usuario lo fija el caso de uso según el rol; {@code null} en
 * los tres significa "todos" (solo el administrador).
 */
public record HistorialPagosQuery(
        UUID compradorId,
        UUID vendedorId,
        UUID domiciliarioId,
        TipoPago tipoFijo,
        MedioPago medio,
        EstadoPago estado,
        TipoPago tipo,
        Instant desde,
        Instant hastaExclusivo,
        boolean ordenarPorMonto,
        boolean ascendente,
        int offset,
        int limit
) {}
