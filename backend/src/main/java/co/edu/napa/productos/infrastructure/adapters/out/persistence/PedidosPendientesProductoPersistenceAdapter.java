package co.edu.napa.productos.infrastructure.adapters.out.persistence;

import co.edu.napa.productos.application.ports.out.PedidosPendientesProductoPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.UUID;

@Repository
public class PedidosPendientesProductoPersistenceAdapter implements PedidosPendientesProductoPort {
    private final JdbcTemplate jdbc;

    public PedidosPendientesProductoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public long contar(UUID productoId) {
        Long count = jdbc.queryForObject("""
                SELECT count(DISTINCT pedido.id)
                FROM pedido
                JOIN item_pedido ON item_pedido.pedido_id = pedido.id
                WHERE item_pedido.producto_id = ?
                    AND pedido.estado IN ('PENDIENTE', 'ACEPTADO', 'LISTO_PARA_RECOGER', 'EN_CAMINO')
                """, Long.class, productoId);
        if (count == null) throw new IllegalStateException("La consulta de pedidos pendientes no devolvió un conteo.");
        return count;
    }
}
