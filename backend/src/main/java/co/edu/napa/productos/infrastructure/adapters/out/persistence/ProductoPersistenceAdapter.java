package co.edu.napa.productos.infrastructure.adapters.out.persistence;

import co.edu.napa.productos.application.ports.in.ProductosPort.Consulta;
import co.edu.napa.productos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.productos.domain.model.EstadoProducto;
import co.edu.napa.productos.domain.model.Producto;
import co.edu.napa.productos.domain.model.UnidadProducto;
import co.edu.napa.shared.domain.DomainException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductoPersistenceAdapter implements ProductoRepositoryPort {
    private static final RowMapper<Producto> MAPPER = (row, index) -> new Producto(
            row.getObject("id", UUID.class), row.getObject("emprendimiento_id", UUID.class),
            row.getString("nombre"), row.getLong("precio"), UnidadProducto.valueOf(row.getString("unidad")),
            row.getString("descripcion"), row.getBigDecimal("cantidad_disponible"),
            EstadoProducto.valueOf(row.getString("estado")), row.getTimestamp("created_at").toInstant(),
            row.getTimestamp("updated_at").toInstant(), row.getLong("version"));
    private final JdbcTemplate jdbc;

    public ProductoPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Producto insertar(Producto value) {
        try {
            jdbc.update("""
                    INSERT INTO producto (id, emprendimiento_id, nombre, precio, unidad, descripcion,
                        cantidad_disponible, estado, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.id(), value.emprendimientoId(), value.nombre(), value.precio(), value.unidad().name(),
                    value.descripcion(), value.cantidadDisponible(), value.estado().name(),
                    Timestamp.from(value.createdAt()), Timestamp.from(value.updatedAt()));
            return value;
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    public Optional<Producto> bloquear(UUID id) {
        return jdbc.query("SELECT * FROM producto WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    public Producto actualizarInventario(Producto value) {
        return updated(jdbc.query("""
                UPDATE producto SET cantidad_disponible = ?, estado = ?, updated_at = ?, version = version + 1
                WHERE id = ? AND version = ? AND estado <> 'ELIMINADO' RETURNING *
                """, MAPPER, value.cantidadDisponible(), value.estado().name(), Timestamp.from(value.updatedAt()),
                value.id(), value.version()));
    }

    public boolean descontar(UUID productoId, java.math.BigDecimal cantidad, java.time.Instant now) {
        return jdbc.update("""
                UPDATE producto
                SET cantidad_disponible = cantidad_disponible - ?,
                    estado = CASE WHEN cantidad_disponible - ? = 0 THEN 'AGOTADO' ELSE 'DISPONIBLE' END,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND estado = 'DISPONIBLE' AND cantidad_disponible >= ?
                """, cantidad, cantidad, Timestamp.from(now), productoId, cantidad) == 1;
    }

    @Override
    public boolean devolver(UUID productoId, java.math.BigDecimal cantidad, java.time.Instant now) {
        return jdbc.update("""
                UPDATE producto
                SET cantidad_disponible = cantidad_disponible + ?,
                    estado = 'DISPONIBLE',
                    updated_at = ?, version = version + 1
                WHERE id = ? AND estado <> 'ELIMINADO'
                """, cantidad, Timestamp.from(now), productoId) == 1;
    }

    public Optional<Producto> buscar(UUID id) {
        return jdbc.query("SELECT * FROM producto WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Producto actualizarDatos(Producto value) {
        try {
            return updated(jdbc.query("""
                    UPDATE producto SET nombre = ?, precio = ?, descripcion = ?, updated_at = ?, version = version + 1
                    WHERE id = ? AND version = ? AND estado <> 'ELIMINADO' RETURNING *
                    """, MAPPER, value.nombre(), value.precio(), value.descripcion(), Timestamp.from(value.updatedAt()),
                    value.id(), value.version()));
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    public void eliminar(Producto value) {
        updated(jdbc.query("""
                UPDATE producto SET estado = 'ELIMINADO', updated_at = ?, version = version + 1
                WHERE id = ? AND version = ? AND estado <> 'ELIMINADO' RETURNING *
                """, MAPPER, Timestamp.from(value.updatedAt()), value.id(), value.version()));
    }

    private Producto updated(java.util.List<Producto> rows) {
        if (rows.size() != 1) {
            throw new DomainException("CONFLICT", "El producto cambió durante la operación. Actualiza sus datos.");
        }
        return rows.getFirst();
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof PSQLException sql
                && sql.getServerErrorMessage() != null
                && "uq_producto_nombre_activo".equals(sql.getServerErrorMessage().getConstraint())) {
            return new DomainException("CONFLICT", "Ya tienes un producto con ese nombre.");
        }
        return exception;
    }

    public Resultado listar(UUID emprendimientoId, Consulta query) {
        var args = new ArrayList<Object>();
        args.add(emprendimientoId);
        String where = " WHERE emprendimiento_id = ? AND estado <> 'ELIMINADO'";
        if (query.estado() != null) {
            where += " AND estado = ?";
            args.add(query.estado().name());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM producto" + where, Long.class, args.toArray());
        // Consulta valida el orden antes de llegar al adaptador; los identificadores salen de esta lista fija.
        String field = query.sort().startsWith("precio,") ? "precio" : "lower(nombre)";
        String direction = query.sort().endsWith(",desc") ? "DESC" : "ASC";
        args.add(query.size());
        args.add((long) query.page() * query.size());
        var content = jdbc.query("SELECT * FROM producto" + where
                + " ORDER BY CASE WHEN estado = 'DISPONIBLE' THEN 0 ELSE 1 END, "
                + field + " " + direction + ", id ASC LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return new Resultado(content, total == null ? 0 : total);
    }

    public List<Producto> consultarDisponibilidad(UUID emprendimientoId, List<UUID> productoIds) {
        if (productoIds.isEmpty()) return List.of();
        String placeholders = String.join(", ", Collections.nCopies(productoIds.size(), "?"));
        var args = new ArrayList<Object>();
        args.add(emprendimientoId);
        args.addAll(productoIds);
        return jdbc.query("SELECT * FROM producto WHERE emprendimiento_id = ? AND id IN (" + placeholders
                + ") AND estado <> 'ELIMINADO' ORDER BY id", MAPPER, args.toArray());
    }

    public List<UUID> bloquearDisponibles(UUID emprendimientoId, List<UUID> productoIds) {
        if (productoIds.isEmpty()) return List.of();
        String placeholders = String.join(", ", Collections.nCopies(productoIds.size(), "?"));
        var args = new ArrayList<Object>();
        args.add(emprendimientoId);
        args.addAll(productoIds);
        return jdbc.query("SELECT id FROM producto WHERE emprendimiento_id = ? AND id IN (" + placeholders
                + ") AND estado <> 'ELIMINADO' ORDER BY id FOR UPDATE",
                (row, index) -> row.getObject("id", UUID.class), args.toArray());
    }
}
