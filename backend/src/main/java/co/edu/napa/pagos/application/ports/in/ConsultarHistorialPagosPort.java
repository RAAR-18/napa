package co.edu.napa.pagos.application.ports.in;

import co.edu.napa.pagos.domain.model.EstadoPago;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.TipoPago;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Historial de pagos: el propio (RF-40) y el general del administrador (RF-41). */
public interface ConsultarHistorialPagosPort {
    Pagina<PagoPropio> consultarPropio(UUID usuarioId, RolHistorial rol, Filtros filtros);

    Pagina<PagoGeneral> consultarGeneral(Filtros filtros);

    enum RolHistorial { CLIENTE, VENDEDOR, DOMICILIARIO }

    /** {@code hasta} es inclusiva; las fechas se interpretan en la hora de Colombia. */
    record Filtros(
            MedioPago medio,
            EstadoPago estado,
            TipoPago tipo,
            LocalDate desde,
            LocalDate hasta,
            boolean ordenarPorMonto,
            boolean ascendente,
            int page,
            int size
    ) {}

    record Pagina<T>(List<T> content, int page, int size, long totalElements) {}

    /** {@code modalidad} solo llega al vendedor y solo en pagos de pedido. */
    record PagoPropio(
            UUID id,
            String tipo,
            long monto,
            String medio,
            String estado,
            Instant createdAt,
            Instant confirmadoAt,
            UUID pedidoId,
            UUID domicilioId,
            String modalidad
    ) {}

    record PagoGeneral(
            UUID id,
            String tipo,
            long monto,
            String medio,
            String estado,
            Instant createdAt,
            Instant confirmadoAt,
            UUID pedidoId,
            UUID domicilioId,
            Usuario comprador,
            Usuario vendedor,
            Usuario domiciliario
    ) {}

    record Usuario(UUID id, String nombre) {}
}
