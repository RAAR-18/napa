package co.edu.napa.pagos.infrastructure.adapters.out.persistence;

import co.edu.napa.pagos.application.ports.out.HistorialPagosQuery;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.domain.model.EstadoPago;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.pagos.domain.model.TipoPago;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PagoPersistenceAdapter implements PagoRepositoryPort {
    private final JdbcTemplate jdbc;

    public PagoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Pago guardar(Pago pago) {
        jdbc.update("""
                INSERT INTO pago (id, tipo, monto, medio, estado, confirmado_at, pedido_id, domicilio_id,
                    comprador_id, vendedor_id, domiciliario_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                pago.id(), pago.tipo().name(), pago.monto(), pago.medio().name(), pago.estado().name(),
                pago.confirmadoAt() == null ? null : Timestamp.from(pago.confirmadoAt()),
                pago.pedidoId(), pago.domicilioId(), pago.compradorId(), pago.vendedorId(),
                pago.domiciliarioId(), Timestamp.from(pago.createdAt()), Timestamp.from(pago.updatedAt()));
        return pago;
    }

    @Override
    public Optional<EstadoPago> consultarEstadoPorPedido(UUID pedidoId) {
        return jdbc.query("""
                SELECT estado FROM pago WHERE pedido_id = ? AND tipo = 'PEDIDO'
                """, result -> result.next()
                ? Optional.of(EstadoPago.valueOf(result.getString("estado")))
                : Optional.empty(), pedidoId);
    }

    private static final String COLUMNAS = """
            pago.id, pago.tipo, pago.monto, pago.medio, pago.estado, pago.confirmado_at, pago.pedido_id,
            pago.domicilio_id, pago.comprador_id, pago.vendedor_id, pago.domiciliario_id, pago.created_at,
            pago.updated_at
            """;

    @Override
    public Optional<Pago> bloquearPorPedido(UUID pedidoId) {
        return uno("SELECT " + COLUMNAS + " FROM pago WHERE pedido_id = ? AND tipo = 'PEDIDO' FOR UPDATE",
                pedidoId);
    }

    @Override
    public Optional<Pago> bloquearTarifaPorDomicilio(UUID domicilioId) {
        return uno("SELECT " + COLUMNAS
                + " FROM pago WHERE domicilio_id = ? AND tipo = 'TARIFA_DOMICILIO' FOR UPDATE", domicilioId);
    }

    @Override
    public Optional<Pago> buscarTarifaPorDomicilio(UUID domicilioId) {
        return uno("SELECT " + COLUMNAS + " FROM pago WHERE domicilio_id = ? AND tipo = 'TARIFA_DOMICILIO'",
                domicilioId);
    }

    /** La modalidad se lee de {@code pedido} en la misma consulta para no crear un ciclo pagos → pedidos. */
    @Override
    public List<FilaHistorial> listarHistorial(HistorialPagosQuery query) {
        List<Object> args = new ArrayList<>();
        String where = where(query, args);
        String orden = (query.ordenarPorMonto() ? "pago.monto" : "pago.created_at")
                + (query.ascendente() ? " ASC" : " DESC") + ", pago.id";
        args.add(query.limit());
        args.add(query.offset());
        return jdbc.query("SELECT " + COLUMNAS + ", pedido.modalidad AS modalidad FROM pago"
                        + " LEFT JOIN pedido ON pedido.id = pago.pedido_id" + where
                        + " ORDER BY " + orden + " LIMIT ? OFFSET ?",
                (result, row) -> new FilaHistorial(pago(result), result.getString("modalidad")),
                args.toArray());
    }

    @Override
    public long contarHistorial(HistorialPagosQuery query) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM pago" + where(query, args), Long.class,
                args.toArray());
        return total == null ? 0 : total;
    }

    private static String where(HistorialPagosQuery query, List<Object> args) {
        List<String> condiciones = new ArrayList<>();
        condicion(condiciones, args, "pago.comprador_id = ?", query.compradorId());
        condicion(condiciones, args, "pago.vendedor_id = ?", query.vendedorId());
        condicion(condiciones, args, "pago.domiciliario_id = ?", query.domiciliarioId());
        condicion(condiciones, args, "pago.tipo = ?", query.tipoFijo() == null ? null : query.tipoFijo().name());
        condicion(condiciones, args, "pago.medio = ?", query.medio() == null ? null : query.medio().name());
        condicion(condiciones, args, "pago.estado = ?", query.estado() == null ? null : query.estado().name());
        condicion(condiciones, args, "pago.tipo = ?", query.tipo() == null ? null : query.tipo().name());
        condicion(condiciones, args, "pago.created_at >= ?",
                query.desde() == null ? null : Timestamp.from(query.desde()));
        condicion(condiciones, args, "pago.created_at < ?",
                query.hastaExclusivo() == null ? null : Timestamp.from(query.hastaExclusivo()));
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    private static void condicion(List<String> condiciones, List<Object> args, String sql, Object value) {
        if (value != null) {
            condiciones.add(sql);
            args.add(value);
        }
    }

    private Optional<Pago> uno(String sql, Object... args) {
        return jdbc.query(sql, result -> result.next() ? Optional.of(pago(result)) : Optional.empty(), args);
    }

    private Pago pago(ResultSet result) throws SQLException {
        return new Pago(
                result.getObject("id", UUID.class),
                TipoPago.valueOf(result.getString("tipo")),
                result.getLong("monto"),
                MedioPago.valueOf(result.getString("medio")),
                EstadoPago.valueOf(result.getString("estado")),
                instant(result.getTimestamp("confirmado_at")),
                result.getObject("pedido_id", UUID.class),
                result.getObject("domicilio_id", UUID.class),
                result.getObject("comprador_id", UUID.class),
                result.getObject("vendedor_id", UUID.class),
                result.getObject("domiciliario_id", UUID.class),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }

    @Override
    public void actualizar(Pago pago) {
        int updated = jdbc.update("""
                UPDATE pago SET estado = ?, confirmado_at = ?, updated_at = ?
                WHERE id = ? AND estado IN ('PENDIENTE_CONFIRMACION', 'REGISTRADO')
                """, pago.estado().name(), Timestamp.from(pago.confirmadoAt()),
                Timestamp.from(pago.updatedAt()), pago.id());
        if (updated != 1) {
            throw new IllegalStateException("El pago cambió durante la confirmación.");
        }
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
