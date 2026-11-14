package co.edu.napa.reportes.infrastructure.adapters.out.persistence;

import co.edu.napa.reportes.application.ports.out.RelacionesReportePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Solo lectura de pedidos, domicilios y usuarios. */
@Repository
public class RelacionesReportePersistenceAdapter implements RelacionesReportePort {
    private final JdbcTemplate jdbc;

    public RelacionesReportePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean participaEnPedido(UUID autorId, UUID pedidoId) {
        return existe("""
                SELECT EXISTS (
                    SELECT 1 FROM pedido p JOIN emprendimiento e ON e.id = p.emprendimiento_id
                    WHERE p.id = ? AND (p.usuario_id = ? OR e.usuario_id = ? OR EXISTS (
                        SELECT 1 FROM domicilio d WHERE d.pedido_id = p.id AND d.domiciliario_id = ?))
                )
                """, pedidoId, autorId, autorId, autorId);
    }

    @Override
    public boolean participaEnDomicilio(UUID autorId, UUID domicilioId) {
        return existe("""
                SELECT EXISTS (
                    SELECT 1 FROM domicilio
                    WHERE id = ? AND (comprador_id = ? OR vendedor_id = ? OR domiciliario_id = ?)
                )
                """, domicilioId, autorId, autorId, autorId);
    }

    @Override
    public boolean compartieronPedido(UUID autorId, UUID usuarioId) {
        return existe("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pedido p
                    JOIN emprendimiento e ON e.id = p.emprendimiento_id
                    LEFT JOIN domicilio d ON d.pedido_id = p.id AND d.domiciliario_id IS NOT NULL
                    WHERE ? IN (p.usuario_id, e.usuario_id, d.domiciliario_id)
                      AND ? IN (p.usuario_id, e.usuario_id, d.domiciliario_id)
                )
                """, autorId, usuarioId);
    }

    @Override
    public Optional<Usuario> usuario(UUID usuarioId) {
        return jdbc.query("SELECT id, nombre, rol, estado FROM usuario WHERE id = ?",
                (rs, i) -> new Usuario(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getString("rol"), rs.getString("estado")),
                usuarioId).stream().findFirst();
    }

    @Override
    public Optional<Map<String, Object>> resumenPedido(UUID pedidoId) {
        return jdbc.query("""
                        SELECT p.estado, p.modalidad, p.total, e.id AS emprendimiento_id, e.nombre AS emprendimiento,
                               c.id AS cliente_id, c.nombre AS cliente
                        FROM pedido p
                        JOIN emprendimiento e ON e.id = p.emprendimiento_id
                        JOIN usuario c ON c.id = p.usuario_id
                        WHERE p.id = ?
                        """,
                (rs, i) -> {
                    Map<String, Object> resumen = new LinkedHashMap<>();
                    resumen.put("estado", rs.getString("estado"));
                    resumen.put("modalidad", rs.getString("modalidad"));
                    resumen.put("emprendimiento", referencia(rs.getObject("emprendimiento_id", UUID.class),
                            rs.getString("emprendimiento")));
                    resumen.put("cliente", referencia(rs.getObject("cliente_id", UUID.class), rs.getString("cliente")));
                    resumen.put("total", rs.getLong("total"));
                    return resumen;
                }, pedidoId).stream().findFirst();
    }

    @Override
    public Optional<Map<String, Object>> resumenDomicilio(UUID domicilioId) {
        return jdbc.query("""
                        SELECT d.estado, d.pedido_id, u.id AS domiciliario_id, u.nombre AS domiciliario
                        FROM domicilio d
                        LEFT JOIN usuario u ON u.id = d.domiciliario_id
                        WHERE d.id = ?
                        """,
                (rs, i) -> {
                    Map<String, Object> resumen = new LinkedHashMap<>();
                    resumen.put("estado", rs.getString("estado"));
                    resumen.put("pedidoId", rs.getObject("pedido_id", UUID.class));
                    UUID domiciliario = rs.getObject("domiciliario_id", UUID.class);
                    resumen.put("domiciliario", domiciliario == null ? null
                            : referencia(domiciliario, rs.getString("domiciliario")));
                    return resumen;
                }, domicilioId).stream().findFirst();
    }

    private static Map<String, Object> referencia(UUID id, String nombre) {
        Map<String, Object> referencia = new LinkedHashMap<>();
        referencia.put("id", id);
        referencia.put("nombre", nombre);
        return referencia;
    }

    private boolean existe(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
