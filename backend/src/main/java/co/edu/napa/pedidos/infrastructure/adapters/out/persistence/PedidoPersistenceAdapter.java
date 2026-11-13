package co.edu.napa.pedidos.infrastructure.adapters.out.persistence;

import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.application.ports.out.FiltroPedidosVendedor;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoListadoProjection;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ItemPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PedidoPersistenceAdapter implements PedidoRepositoryPort {
    /** Pedidos del vendedor: los de su emprendimiento activo (RF-50, RF-51). */
    private static final String VENDEDOR = "e.usuario_id = ? AND e.estado = 'ACTIVO'";
    /** Pedidos del cliente: los que hizo, aunque el emprendimiento ya no esté activo (RF-47, RF-48). */
    private static final String CLIENTE = "p.usuario_id = ?";
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

    @Override
    public long contarSinCerrarDeEmprendimiento(UUID emprendimientoId) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pedido
                WHERE emprendimiento_id = ?
                  AND estado IN ('PENDIENTE', 'ACEPTADO', 'LISTO_PARA_RECOGER', 'EN_CAMINO')
                """, Long.class, emprendimientoId);
        return total == null ? 0 : total;
    }

    @Override
    public int contarItems(UUID pedidoId) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*)::int FROM item_pedido WHERE pedido_id = ?",
                Integer.class, pedidoId);
        return total == null ? 0 : total;
    }

    @Override
    public Optional<Pedido> bloquearPorId(UUID pedidoId) {
        List<PedidoRow> rows = jdbc.query("""
                SELECT id, usuario_id, emprendimiento_id, modalidad, estado, reserva_at, medio_pago,
                    tarifa_domicilio, total, ubicacion_entrega_id, created_at, updated_at
                FROM pedido WHERE id = ? FOR UPDATE
                """, (row, index) -> new PedidoRow(
                row.getObject("id", UUID.class),
                row.getObject("usuario_id", UUID.class),
                row.getObject("emprendimiento_id", UUID.class),
                ModalidadEntrega.valueOf(row.getString("modalidad")),
                EstadoPedido.valueOf(row.getString("estado")),
                instant(row.getTimestamp("reserva_at")),
                MedioPagoPedido.valueOf(row.getString("medio_pago")),
                (Long) row.getObject("tarifa_domicilio"),
                row.getLong("total"),
                row.getObject("ubicacion_entrega_id", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant()
        ), pedidoId);
        if (rows.isEmpty()) return Optional.empty();

        PedidoRow pedido = rows.getFirst();
        List<ItemPedido> items = jdbc.query("""
                SELECT id, producto_id, cantidad, precio_unitario
                FROM item_pedido WHERE pedido_id = ? ORDER BY producto_id
                """, (row, index) -> new ItemPedido(
                row.getObject("id", UUID.class),
                row.getObject("producto_id", UUID.class),
                row.getBigDecimal("cantidad"),
                row.getLong("precio_unitario")
        ), pedidoId);
        return Optional.of(new Pedido(pedido.id(), pedido.compradorId(), pedido.emprendimientoId(),
                pedido.modalidad(), pedido.estado(), pedido.reserva(), pedido.medioPago(),
                pedido.tarifaDomicilio(), pedido.total(), pedido.ubicacionEntregaId(), items,
                pedido.createdAt(), pedido.updatedAt()));
    }

    @Override
    public void actualizar(Pedido pedido) {
        int updated = jdbc.update("""
                UPDATE pedido SET estado = ?, updated_at = ? WHERE id = ?
                """, pedido.estado().name(), Timestamp.from(pedido.updatedAt()), pedido.id());
        if (updated != 1) {
            throw new DomainException("CONFLICT", "El pedido cambió durante la operación.");
        }
    }

    @Override
    public List<PedidoListadoProjection> listarPedidosVendedor(
            UUID vendedorId,
            FiltroPedidosVendedor filtro,
            long offset,
            int limit
    ) {
        return listar(VENDEDOR, vendedorId, filtro, offset, limit);
    }

    @Override
    public long contarPedidosVendedor(UUID vendedorId, FiltroPedidosVendedor filtro) {
        return contar(VENDEDOR, vendedorId, filtro);
    }

    @Override
    public List<PedidoListadoProjection> listarPedidosCliente(
            UUID clienteId,
            FiltroPedidosVendedor filtro,
            long offset,
            int limit
    ) {
        return listar(CLIENTE, clienteId, filtro, offset, limit);
    }

    @Override
    public long contarPedidosCliente(UUID clienteId, FiltroPedidosVendedor filtro) {
        return contar(CLIENTE, clienteId, filtro);
    }

    @Override
    public Optional<PedidoDetalleProjection> consultarDetalleCliente(UUID clienteId, UUID pedidoId) {
        return consultarDetalle(CLIENTE, clienteId, pedidoId);
    }

    private List<PedidoListadoProjection> listar(
            String owner,
            UUID ownerId,
            FiltroPedidosVendedor filtro,
            long offset,
            int limit
    ) {
        List<Object> parameters = new ArrayList<>();
        String where = ownerAndFilters(owner, ownerId, filtro, parameters);
        parameters.add(limit);
        parameters.add(offset);
        String direction = filtro.ascendente() ? "ASC" : "DESC";
        return jdbc.query("""
                SELECT p.id, p.usuario_id AS cliente_id, p.created_at, p.estado, p.modalidad,
                    p.reserva_at, p.total, e.id AS emprendimiento_id, e.nombre AS emprendimiento_nombre
                """ + where + " ORDER BY p.created_at " + direction
                + ", p.id ASC LIMIT ? OFFSET ?", (row, index) -> new PedidoListadoProjection(
                row.getObject("id", UUID.class),
                row.getObject("cliente_id", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                EstadoPedido.valueOf(row.getString("estado")),
                ModalidadEntrega.valueOf(row.getString("modalidad")),
                instant(row.getTimestamp("reserva_at")),
                row.getLong("total"),
                row.getObject("emprendimiento_id", UUID.class),
                row.getString("emprendimiento_nombre")
        ), parameters.toArray());
    }

    private long contar(String owner, UUID ownerId, FiltroPedidosVendedor filtro) {
        List<Object> parameters = new ArrayList<>();
        String where = ownerAndFilters(owner, ownerId, filtro, parameters);
        return jdbc.queryForObject("SELECT count(*) " + where, Long.class, parameters.toArray());
    }

    @Override
    public Optional<PedidoDetalleProjection> consultarDetalleVendedor(UUID vendedorId, UUID pedidoId) {
        return consultarDetalle("e.usuario_id = ?", vendedorId, pedidoId);
    }

    private Optional<PedidoDetalleProjection> consultarDetalle(String owner, UUID ownerId, UUID pedidoId) {
        List<PedidoDetalleRow> rows = jdbc.query("""
                SELECT p.id, p.usuario_id AS cliente_id, p.estado, p.modalidad, p.reserva_at,
                    p.created_at, p.updated_at, p.medio_pago, p.tarifa_domicilio, p.total,
                    ubicacion.latitud, ubicacion.longitud, ubicacion.direccion, ubicacion.referencia, ubicacion.barrio,
                    producto.id AS producto_id, producto.nombre AS producto_nombre,
                    producto.unidad AS producto_unidad, producto.estado AS producto_estado,
                    item.cantidad, item.precio_unitario,
                    e.id AS emprendimiento_id, e.nombre AS emprendimiento_nombre
                FROM pedido p
                JOIN emprendimiento e ON e.id = p.emprendimiento_id
                JOIN item_pedido item ON item.pedido_id = p.id
                JOIN producto ON producto.id = item.producto_id
                LEFT JOIN ubicacion ON ubicacion.id = p.ubicacion_entrega_id
                    AND p.estado NOT IN ('RECHAZADO', 'ENTREGADO', 'CANCELADO')
                WHERE p.id = ? AND\s""" + owner + "\n" + """
                ORDER BY item.producto_id
                """, (row, index) -> new PedidoDetalleRow(
                row.getObject("id", UUID.class),
                row.getObject("cliente_id", UUID.class),
                EstadoPedido.valueOf(row.getString("estado")),
                ModalidadEntrega.valueOf(row.getString("modalidad")),
                instant(row.getTimestamp("reserva_at")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                MedioPagoPedido.valueOf(row.getString("medio_pago")),
                row.getObject("tarifa_domicilio", Long.class),
                row.getLong("total"),
                row.getObject("latitud", Double.class),
                row.getObject("longitud", Double.class),
                row.getString("direccion"),
                row.getString("referencia"),
                row.getString("barrio"),
                row.getObject("producto_id", UUID.class),
                row.getString("producto_nombre"),
                row.getString("producto_unidad"),
                "ELIMINADO".equals(row.getString("producto_estado")),
                row.getBigDecimal("cantidad"),
                row.getLong("precio_unitario"),
                row.getObject("emprendimiento_id", UUID.class),
                row.getString("emprendimiento_nombre")
        ), pedidoId, ownerId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        PedidoDetalleRow first = rows.getFirst();
        List<PedidoDetalleProjection.Item> items = rows.stream()
                .map(row -> new PedidoDetalleProjection.Item(
                        row.productoId(),
                        row.productoNombre(),
                        row.productoUnidad(),
                        row.cantidad(),
                        row.precioUnitario(),
                        row.productoEliminado()))
                .toList();
        PedidoDetalleProjection.Ubicacion location = first.latitud() == null ? null
                : new PedidoDetalleProjection.Ubicacion(
                        first.latitud(), first.longitud(), first.direccion(), first.referencia(), first.barrio());
        return Optional.of(new PedidoDetalleProjection(
                first.id(),
                first.clienteId(),
                first.estado(),
                first.modalidad(),
                first.reserva(),
                first.createdAt(),
                first.updatedAt(),
                first.medioPago(),
                first.tarifaDomicilio(),
                first.total(),
                location,
                items,
                first.emprendimientoId(),
                first.emprendimientoNombre()
        ));
    }

    private String ownerAndFilters(
            String owner,
            UUID ownerId,
            FiltroPedidosVendedor filtro,
            List<Object> parameters
    ) {
        StringBuilder where = new StringBuilder("""
                FROM pedido p
                JOIN emprendimiento e ON e.id = p.emprendimiento_id
                WHERE """).append(" ").append(owner).append('\n');
        parameters.add(ownerId);
        if (filtro.estado() != null) {
            where.append(" AND p.estado = ?");
            parameters.add(filtro.estado().name());
        }
        if (filtro.modalidad() != null) {
            where.append(" AND p.modalidad = ?");
            parameters.add(filtro.modalidad().name());
        }
        if (filtro.reserva() != null) {
            where.append(filtro.reserva() ? " AND p.reserva_at IS NOT NULL" : " AND p.reserva_at IS NULL");
        }
        if (filtro.desde() != null) {
            where.append(" AND p.created_at >= ?");
            parameters.add(Timestamp.from(filtro.desde()));
        }
        if (filtro.hasta() != null) {
            where.append(" AND p.created_at < ?");
            parameters.add(Timestamp.from(filtro.hasta()));
        }
        return where.toString();
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record PedidoDetalleRow(
            UUID id,
            UUID clienteId,
            EstadoPedido estado,
            ModalidadEntrega modalidad,
            Instant reserva,
            Instant createdAt,
            Instant updatedAt,
            MedioPagoPedido medioPago,
            Long tarifaDomicilio,
            long total,
            Double latitud,
            Double longitud,
            String direccion,
            String referencia,
            String barrio,
            UUID productoId,
            String productoNombre,
            String productoUnidad,
            boolean productoEliminado,
            java.math.BigDecimal cantidad,
            long precioUnitario,
            UUID emprendimientoId,
            String emprendimientoNombre
    ) {}

    private record PedidoRow(
            UUID id,
            UUID compradorId,
            UUID emprendimientoId,
            ModalidadEntrega modalidad,
            EstadoPedido estado,
            Instant reserva,
            MedioPagoPedido medioPago,
            Long tarifaDomicilio,
            long total,
            UUID ubicacionEntregaId,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
