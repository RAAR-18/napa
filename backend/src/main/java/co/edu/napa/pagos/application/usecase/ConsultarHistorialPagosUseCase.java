package co.edu.napa.pagos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort;
import co.edu.napa.pagos.application.ports.out.HistorialPagosQuery;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort.FilaHistorial;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.pagos.domain.model.TipoPago;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * RF-40 y RF-41. El filtro por usuario sale del rol del JWT y no se puede quitar desde los parámetros: el cliente ve
 * los pagos de sus pedidos, el vendedor sus cobros y las tarifas que pagó, y el domiciliario sus tarifas.
 */
public class ConsultarHistorialPagosUseCase implements ConsultarHistorialPagosPort {
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    public static final int MAXIMO_POR_PAGINA = 100;

    private final PagoRepositoryPort pagos;
    private final ConsultarNombresUsuarioPort usuarios;

    public ConsultarHistorialPagosUseCase(PagoRepositoryPort pagos, ConsultarNombresUsuarioPort usuarios) {
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PagoPropio> consultarPropio(UUID usuarioId, RolHistorial rol, Filtros filtros) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(rol, "rol");
        HistorialPagosQuery query = switch (rol) {
            case CLIENTE -> query(filtros, usuarioId, null, null, TipoPago.PEDIDO);
            case VENDEDOR -> query(filtros, null, usuarioId, null, null);
            case DOMICILIARIO -> query(filtros, null, null, usuarioId, TipoPago.TARIFA_DOMICILIO);
        };
        List<PagoPropio> content = pagos.listarHistorial(query).stream().map(fila -> {
            Pago pago = fila.pago();
            String modalidad = rol == RolHistorial.VENDEDOR && pago.tipo() == TipoPago.PEDIDO ? fila.modalidad() : null;
            return new PagoPropio(pago.id(), pago.tipo().name(), pago.monto(), pago.medio().name(),
                    pago.estado().name(), pago.createdAt(), pago.confirmadoAt(), pago.pedidoId(), pago.domicilioId(),
                    modalidad);
        }).toList();
        return new Pagina<>(content, filtros.page(), filtros.size(), pagos.contarHistorial(query));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PagoGeneral> consultarGeneral(Filtros filtros) {
        HistorialPagosQuery query = query(filtros, null, null, null, null);
        List<FilaHistorial> filas = pagos.listarHistorial(query);
        Set<UUID> ids = new HashSet<>();
        filas.forEach(fila -> Stream.of(fila.pago().compradorId(), fila.pago().vendedorId(),
                fila.pago().domiciliarioId()).filter(Objects::nonNull).forEach(ids::add));
        Map<UUID, String> nombres = ids.isEmpty() ? Map.of() : usuarios.consultar(ids);
        List<PagoGeneral> content = filas.stream().map(fila -> {
            Pago pago = fila.pago();
            boolean tarifa = pago.tipo() == TipoPago.TARIFA_DOMICILIO;
            return new PagoGeneral(pago.id(), pago.tipo().name(), pago.monto(), pago.medio().name(),
                    pago.estado().name(), pago.createdAt(), pago.confirmadoAt(), pago.pedidoId(), pago.domicilioId(),
                    tarifa ? null : usuario(pago.compradorId(), nombres),
                    usuario(pago.vendedorId(), nombres),
                    tarifa ? usuario(pago.domiciliarioId(), nombres) : null);
        }).toList();
        return new Pagina<>(content, filtros.page(), filtros.size(), pagos.contarHistorial(query));
    }

    private static HistorialPagosQuery query(
            Filtros filtros,
            UUID compradorId,
            UUID vendedorId,
            UUID domiciliarioId,
            TipoPago tipoFijo
    ) {
        Objects.requireNonNull(filtros, "filtros");
        if (filtros.page() < 0) throw invalid("page", "La página no puede ser negativa.");
        if (filtros.size() < 1 || filtros.size() > MAXIMO_POR_PAGINA) {
            throw invalid("size", "El tamaño de página debe estar entre 1 y " + MAXIMO_POR_PAGINA + ".");
        }
        if (filtros.desde() != null && filtros.hasta() != null && filtros.desde().isAfter(filtros.hasta())) {
            throw invalid("desde", "La fecha inicial no puede ser posterior a la fecha final.");
        }
        Instant desde = filtros.desde() == null ? null : filtros.desde().atStartOfDay(COLOMBIA).toInstant();
        Instant hasta = filtros.hasta() == null ? null : filtros.hasta().plusDays(1).atStartOfDay(COLOMBIA).toInstant();
        long offset = (long) filtros.page() * filtros.size();
        if (offset > Integer.MAX_VALUE) throw invalid("page", "La página está fuera del rango permitido.");
        return new HistorialPagosQuery(compradorId, vendedorId, domiciliarioId, tipoFijo, filtros.medio(),
                filtros.estado(), filtros.tipo(), desde, hasta, filtros.ordenarPorMonto(), filtros.ascendente(),
                (int) offset, filtros.size());
    }

    private static Usuario usuario(UUID id, Map<UUID, String> nombres) {
        return id == null ? null : new Usuario(id, nombres.get(id));
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
