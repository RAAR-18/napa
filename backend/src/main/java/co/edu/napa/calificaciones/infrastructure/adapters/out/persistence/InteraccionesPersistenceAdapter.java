package co.edu.napa.calificaciones.infrastructure.adapters.out.persistence;

import co.edu.napa.calificaciones.application.ports.out.InteraccionesPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

/** Solo lectura de las tablas de pedidos, domicilios, emprendimientos, productos y usuarios. */
@Repository
public class InteraccionesPersistenceAdapter implements InteraccionesPort {
    private final JdbcTemplate jdbc;

    public InteraccionesPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ParticipantesPedido> participantes(UUID pedidoId) {
        return jdbc.query("""
                        SELECT p.id, p.usuario_id AS cliente_id, e.usuario_id AS vendedor_id, p.emprendimiento_id,
                               p.estado,
                               (SELECT d.domiciliario_id FROM domicilio d
                                 WHERE d.pedido_id = p.id AND d.estado = 'ENTREGADO'
                                 ORDER BY d.created_at DESC LIMIT 1) AS domiciliario_id
                        FROM pedido p
                        JOIN emprendimiento e ON e.id = p.emprendimiento_id
                        WHERE p.id = ?
                        """,
                (rs, i) -> new ParticipantesPedido(
                        rs.getObject("id", UUID.class),
                        rs.getObject("cliente_id", UUID.class),
                        rs.getObject("vendedor_id", UUID.class),
                        rs.getObject("emprendimiento_id", UUID.class),
                        rs.getString("estado"),
                        new HashSet<>(jdbc.queryForList(
                                "SELECT producto_id FROM item_pedido WHERE pedido_id = ?", UUID.class, pedidoId)),
                        rs.getObject("domiciliario_id", UUID.class)
                ), pedidoId).stream().findFirst();
    }

    @Override
    public Optional<UUID> pedidoDeDomicilio(UUID domicilioId) {
        return jdbc.queryForList("SELECT pedido_id FROM domicilio WHERE id = ?", UUID.class, domicilioId)
                .stream().findFirst();
    }

    @Override
    public Optional<String> nombre(co.edu.napa.calificaciones.domain.model.TipoEntidad tipo, UUID entidadId) {
        String tabla = switch (tipo) {
            case EMPRENDIMIENTO -> "emprendimiento";
            case PRODUCTO -> "producto";
            case USUARIO -> "usuario";
        };
        return jdbc.queryForList("SELECT nombre FROM " + tabla + " WHERE id = ?", String.class, entidadId)
                .stream().findFirst();
    }

    @Override
    public Optional<UUID> duenioEmprendimiento(UUID emprendimientoId) {
        return jdbc.queryForList("SELECT usuario_id FROM emprendimiento WHERE id = ?", UUID.class, emprendimientoId)
                .stream().findFirst();
    }

    @Override
    public Optional<UUID> duenioProducto(UUID productoId) {
        return jdbc.queryForList("""
                        SELECT e.usuario_id FROM producto p JOIN emprendimiento e ON e.id = p.emprendimiento_id
                        WHERE p.id = ?
                        """, UUID.class, productoId)
                .stream().findFirst();
    }

    @Override
    public Optional<String> rolUsuario(UUID usuarioId) {
        return jdbc.queryForList("SELECT rol FROM usuario WHERE id = ? AND estado <> 'ELIMINADA'",
                        String.class, usuarioId)
                .stream().findFirst();
    }

    @Override
    public java.util.Map<UUID, UUID> duenios(co.edu.napa.calificaciones.domain.model.TipoEntidad tipo,
                                             java.util.Collection<UUID> entidadIds) {
        String sql = switch (tipo) {
            case EMPRENDIMIENTO -> "SELECT id, usuario_id FROM emprendimiento WHERE id = ANY (?)";
            case PRODUCTO -> "SELECT p.id, e.usuario_id FROM producto p JOIN emprendimiento e"
                    + " ON e.id = p.emprendimiento_id WHERE p.id = ANY (?)";
            case USUARIO -> throw new IllegalArgumentException("Los usuarios no tienen dueño");
        };
        java.util.Map<UUID, UUID> resultado = new java.util.HashMap<>();
        jdbc.query(sql, rs -> {
            resultado.put(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class));
        }, (Object) entidadIds.toArray(UUID[]::new));
        return resultado;
    }

    @Override
    public boolean clienteComproAVendedor(UUID vendedorId, UUID clienteId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pedido p JOIN emprendimiento e ON e.id = p.emprendimiento_id
                    WHERE e.usuario_id = ? AND p.usuario_id = ?
                )
                """, Boolean.class, vendedorId, clienteId));
    }

    @Override
    public boolean domiciliarioLlevoPedidoDeVendedor(UUID vendedorId, UUID domiciliarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM domicilio WHERE vendedor_id = ? AND domiciliario_id = ?)",
                Boolean.class, vendedorId, domiciliarioId));
    }
}
