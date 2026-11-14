package co.edu.napa.calificaciones.infrastructure.adapters.out.persistence;

import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.domain.model.AccionModeracion;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CalificacionPersistenceAdapter implements CalificacionRepositoryPort {
    private static final String COLUMNAS = """
            c.id, c.autor_id, c.pedido_id, c.emprendimiento_id, c.producto_id, c.usuario_id, c.puntaje,
            c.comentario, c.estado, c.moderada, c.created_at, c.updated_at
            """;

    private static final RowMapper<Calificacion> CALIFICACION = (rs, i) -> new Calificacion(
            rs.getObject("id", UUID.class),
            rs.getObject("autor_id", UUID.class),
            rs.getObject("pedido_id", UUID.class),
            tipo(rs),
            entidadId(rs),
            rs.getInt("puntaje"),
            rs.getString("comentario"),
            EstadoCalificacion.valueOf(rs.getString("estado")),
            rs.getBoolean("moderada"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private static final RowMapper<CalificacionListada> LISTADA = (rs, i) -> new CalificacionListada(
            rs.getObject("id", UUID.class),
            rs.getInt("puntaje"),
            rs.getString("comentario"),
            EstadoCalificacion.valueOf(rs.getString("estado")),
            rs.getBoolean("moderada"),
            tipo(rs),
            entidadId(rs),
            rs.getString("entidad_nombre"),
            rs.getObject("pedido_id", UUID.class),
            rs.getObject("autor_id", UUID.class),
            rs.getString("autor_nombre"),
            rs.getString("autor_rol"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public CalificacionPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(UUID autorId, UUID pedidoId, TipoEntidad tipoEntidad, UUID entidadId) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM calificacion WHERE autor_id = ? AND pedido_id = ? AND "
                        + columna(tipoEntidad) + " = ?)",
                Boolean.class, autorId, pedidoId, entidadId);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    public List<Calificacion> delAutorEnPedido(UUID autorId, UUID pedidoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM calificacion c WHERE c.autor_id = ? AND c.pedido_id = ?",
                CALIFICACION, autorId, pedidoId);
    }

    @Override
    public void insertar(Calificacion c) {
        try {
            jdbc.update("""
                    INSERT INTO calificacion (id, autor_id, pedido_id, emprendimiento_id, producto_id, usuario_id,
                                              puntaje, comentario, estado, moderada, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    c.id(), c.autorId(), c.pedidoId(),
                    c.tipoEntidad() == TipoEntidad.EMPRENDIMIENTO ? c.entidadId() : null,
                    c.tipoEntidad() == TipoEntidad.PRODUCTO ? c.entidadId() : null,
                    c.tipoEntidad() == TipoEntidad.USUARIO ? c.entidadId() : null,
                    c.puntaje(), c.comentario(), c.estado().name(), c.moderada(),
                    Timestamp.from(c.createdAt()), Timestamp.from(c.updatedAt()));
        } catch (DuplicateKeyException duplicada) {
            throw new DomainException("CONFLICT", "Ya calificaste esto.");
        }
    }

    @Override
    public Optional<Calificacion> buscarParaActualizar(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM calificacion c WHERE c.id = ? FOR UPDATE", CALIFICACION, id)
                .stream().findFirst();
    }

    @Override
    public void actualizar(Calificacion c) {
        jdbc.update("""
                UPDATE calificacion
                SET puntaje = ?, comentario = ?, estado = ?, moderada = ?, updated_at = ?
                WHERE id = ?
                """,
                c.puntaje(), c.comentario(), c.estado().name(), c.moderada(), Timestamp.from(c.updatedAt()), c.id());
    }

    @Override
    public void registrarModeracion(UUID calificacionId, UUID administradorId, AccionModeracion accion,
                                    String motivo, String contenidoAnterior, Instant ahora) {
        jdbc.update("""
                INSERT INTO moderacion_comentario (id, calificacion_id, administrador_id, accion, motivo,
                                                   contenido_anterior, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), calificacionId, administradorId, accion.name(), motivo, contenidoAnterior,
                Timestamp.from(ahora));
    }

    @Override
    public List<CalificacionListada> listar(Filtro filtro, int page, int size, Orden orden) {
        List<Object> args = new ArrayList<>();
        String where = where(filtro, args);
        String campo = "puntaje".equals(orden.campo()) ? "c.puntaje" : "c.created_at";
        String direccion = orden.ascendente() ? "ASC" : "DESC";
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("SELECT " + COLUMNAS + """
                        , a.nombre AS autor_nombre, a.rol AS autor_rol,
                          COALESCE(e.nombre, p.nombre, u.nombre) AS entidad_nombre
                        FROM calificacion c
                        JOIN usuario a ON a.id = c.autor_id
                        LEFT JOIN emprendimiento e ON e.id = c.emprendimiento_id
                        LEFT JOIN producto p ON p.id = c.producto_id
                        LEFT JOIN usuario u ON u.id = c.usuario_id
                        """ + where + " ORDER BY " + campo + " " + direccion + ", c.created_at DESC, c.id DESC"
                        + " LIMIT ? OFFSET ?",
                LISTADA, args.toArray());
    }

    @Override
    public long contar(Filtro filtro) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM calificacion c " + where(filtro, args),
                Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    @Override
    public Resumen resumen(TipoEntidad tipoEntidad, UUID entidadId) {
        return jdbc.queryForObject(
                "SELECT AVG(puntaje)::float8 AS promedio, COUNT(*) AS total FROM calificacion WHERE estado = 'ACTIVA' AND "
                        + columna(tipoEntidad) + " = ?",
                (rs, i) -> new Resumen((Double) rs.getObject("promedio"), rs.getLong("total")),
                entidadId);
    }

    @Override
    public java.util.Map<UUID, Resumen> resumenes(TipoEntidad tipoEntidad, java.util.Collection<UUID> entidadIds) {
        String columna = columna(tipoEntidad);
        java.util.Map<UUID, Resumen> resultado = new java.util.HashMap<>();
        jdbc.query("SELECT " + columna + " AS entidad_id, AVG(puntaje)::float8 AS promedio, COUNT(*) AS total"
                        + " FROM calificacion WHERE estado = 'ACTIVA' AND " + columna + " = ANY (?)"
                        + " GROUP BY " + columna,
                rs -> {
                    resultado.put(rs.getObject("entidad_id", UUID.class),
                            new Resumen(rs.getDouble("promedio"), rs.getLong("total")));
                },
                (Object) entidadIds.toArray(UUID[]::new));
        return resultado;
    }

    private static String where(Filtro filtro, List<Object> args) {
        List<String> condiciones = new ArrayList<>();
        if (filtro.estado() != null) {
            condiciones.add("c.estado = ?");
            args.add(filtro.estado().name());
        }
        if (filtro.tipoEntidad() != null) {
            String columna = "c." + columna(filtro.tipoEntidad());
            if (filtro.entidadId() != null) {
                condiciones.add(columna + " = ?");
                args.add(filtro.entidadId());
            } else {
                condiciones.add(columna + " IS NOT NULL");
            }
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    private static String columna(TipoEntidad tipo) {
        return switch (tipo) {
            case EMPRENDIMIENTO -> "emprendimiento_id";
            case PRODUCTO -> "producto_id";
            case USUARIO -> "usuario_id";
        };
    }

    private static TipoEntidad tipo(ResultSet rs) throws SQLException {
        if (rs.getObject("emprendimiento_id") != null) {
            return TipoEntidad.EMPRENDIMIENTO;
        }
        return rs.getObject("producto_id") != null ? TipoEntidad.PRODUCTO : TipoEntidad.USUARIO;
    }

    private static UUID entidadId(ResultSet rs) throws SQLException {
        return switch (tipo(rs)) {
            case EMPRENDIMIENTO -> rs.getObject("emprendimiento_id", UUID.class);
            case PRODUCTO -> rs.getObject("producto_id", UUID.class);
            case USUARIO -> rs.getObject("usuario_id", UUID.class);
        };
    }
}
