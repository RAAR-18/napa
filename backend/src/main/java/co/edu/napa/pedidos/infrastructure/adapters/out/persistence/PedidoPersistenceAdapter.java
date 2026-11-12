package co.edu.napa.pedidos.infrastructure.adapters.out.persistence;

import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.ItemPedido;
import co.edu.napa.pedidos.domain.model.Pedido;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Objects;

@Repository
public class PedidoPersistenceAdapter implements PedidoRepositoryPort {
    private final JdbcTemplate jdbc;

    public PedidoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void guardar(Pedido pedido) {
        jdbc.update("""
                INSERT INTO pedido (id, usuario_id, emprendimiento_id, modalidad, estado, reserva_at,
                    medio_pago, tarifa_domicilio, total, ubicacion_entrega_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                pedido.id(), pedido.compradorId(), pedido.emprendimientoId(), pedido.modalidad().name(),
                pedido.estado().name(), pedido.reserva() == null ? null : Timestamp.from(pedido.reserva()),
                pedido.medioPago().name(), pedido.tarifaDomicilio(), pedido.total(), pedido.ubicacionEntregaId(),
                Timestamp.from(pedido.createdAt()), Timestamp.from(pedido.updatedAt()));
        for (ItemPedido item : pedido.items()) {
            jdbc.update("""
                    INSERT INTO item_pedido (id, pedido_id, producto_id, cantidad, precio_unitario)
                    VALUES (?, ?, ?, ?, ?)
                    """, item.id(), pedido.id(), item.productoId(), item.cantidad(), item.precioUnitario());
        }
    }
}
