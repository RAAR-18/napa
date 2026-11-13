package co.edu.napa.pagos.infrastructure.adapters.out.persistence;

import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.domain.model.EstadoPago;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.pagos.domain.model.TipoPago;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
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

    @Override
    public Optional<Pago> bloquearPorPedido(UUID pedidoId) {
        return jdbc.query("""
                SELECT id, tipo, monto, medio, estado, confirmado_at, pedido_id, domicilio_id,
                    comprador_id, vendedor_id, domiciliario_id, created_at, updated_at
                FROM pago WHERE pedido_id = ? AND tipo = 'PEDIDO' FOR UPDATE
                """, result -> result.next()
                ? Optional.of(new Pago(
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
                        result.getTimestamp("updated_at").toInstant()))
                : Optional.empty(), pedidoId);
    }

    @Override
    public void actualizar(Pago pago) {
        int updated = jdbc.update("""
                UPDATE pago SET estado = ?, confirmado_at = ?, updated_at = ?
                WHERE id = ? AND estado = 'PENDIENTE_CONFIRMACION'
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
