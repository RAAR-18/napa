package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.application.ports.in.AdministrarUsuariosPort.Filtro;
import co.edu.napa.cuentas.application.ports.in.AdministrarUsuariosPort.UsuarioResumen;
import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;
import co.edu.napa.cuentas.application.ports.out.GestionUsuariosRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GestionUsuariosPersistenceAdapter implements GestionUsuariosRepositoryPort {
    private final JdbcTemplate jdbc;

    public GestionUsuariosPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PerfilUsuario> perfil(UUID usuarioId) {
        return jdbc.query("""
                        SELECT u.id, u.nombre, u.celular, u.correo, u.foto_url, u.rol, u.estado,
                               u.celular_verificado_at, u.created_at,
                               ub.latitud, ub.longitud, ub.direccion, ub.referencia, ub.id AS ubicacion_id
                        FROM usuario u
                        LEFT JOIN ubicacion ub ON ub.id = u.ubicacion_id
                        WHERE u.id = ?
                        """,
                (rs, i) -> new PerfilUsuario(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        rs.getString("celular"),
                        rs.getString("correo"),
                        rs.getString("foto_url"),
                        rs.getString("rol"),
                        rs.getString("estado"),
                        rs.getTimestamp("celular_verificado_at") != null,
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getObject("ubicacion_id") == null ? null : new PerfilUsuario.Ubicacion(
                                rs.getDouble("latitud"), rs.getDouble("longitud"),
                                rs.getString("direccion"), rs.getString("referencia"))
                ), usuarioId).stream().findFirst();
    }

    @Override
    public Optional<RolEstado> bloquear(UUID usuarioId) {
        return jdbc.query("SELECT rol, estado FROM usuario WHERE id = ? FOR UPDATE",
                (rs, i) -> new RolEstado(rs.getString("rol"), rs.getString("estado")), usuarioId).stream().findFirst();
    }

    @Override
    public void actualizarNombre(UUID usuarioId, String nombre, Instant ahora) {
        jdbc.update("UPDATE usuario SET nombre = ?, updated_at = ? WHERE id = ?", nombre, Timestamp.from(ahora), usuarioId);
    }

    @Override
    public void guardarUbicacion(UUID usuarioId, PerfilUsuario.Ubicacion u, Instant ahora) {
        Timestamp fecha = Timestamp.from(ahora);
        UUID actual = jdbc.queryForObject("SELECT ubicacion_id FROM usuario WHERE id = ?", UUID.class, usuarioId);
        if (actual != null) {
            jdbc.update("""
                    UPDATE ubicacion SET latitud = ?, longitud = ?, direccion = ?, referencia = ?, updated_at = ?
                    WHERE id = ?
                    """, u.latitud(), u.longitud(), u.direccion(), u.referencia(), fecha, actual);
        } else {
            UUID nueva = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO ubicacion (id, latitud, longitud, direccion, referencia, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, nueva, u.latitud(), u.longitud(), u.direccion(), u.referencia(), fecha, fecha);
            jdbc.update("UPDATE usuario SET ubicacion_id = ? WHERE id = ?", nueva, usuarioId);
        }
        jdbc.update("UPDATE usuario SET updated_at = ? WHERE id = ?", fecha, usuarioId);
    }

    @Override
    public void actualizarCelular(UUID usuarioId, String celular, Instant ahora) {
        try {
            // El administrador fija el celular sin código (RF-67); queda como verificado.
            jdbc.update("""
                    UPDATE usuario SET celular = ?, celular_verificado_at = COALESCE(celular_verificado_at, ?),
                                       updated_at = ?
                    WHERE id = ?
                    """, celular, Timestamp.from(ahora), Timestamp.from(ahora), usuarioId);
        } catch (DuplicateKeyException e) {
            throw new DomainException("PHONE_ALREADY_REGISTERED", "Ese celular ya está registrado.");
        }
    }

    @Override
    public void actualizarCorreo(UUID usuarioId, String correo, Instant ahora) {
        try {
            jdbc.update("UPDATE usuario SET correo = ?, updated_at = ? WHERE id = ?",
                    correo, Timestamp.from(ahora), usuarioId);
        } catch (DuplicateKeyException e) {
            throw new DomainException("EMAIL_ALREADY_REGISTERED", "Ese correo ya está registrado.");
        }
    }

    @Override
    public void actualizarRol(UUID usuarioId, String rol, Instant ahora) {
        jdbc.update("UPDATE usuario SET rol = ?, updated_at = ? WHERE id = ?", rol, Timestamp.from(ahora), usuarioId);
    }

    @Override
    public void marcarEliminada(UUID usuarioId, Instant ahora) {
        jdbc.update("UPDATE usuario SET estado = 'ELIMINADA', updated_at = ? WHERE id = ?",
                Timestamp.from(ahora), usuarioId);
    }

    @Override
    public void eliminarEmprendimientoActivo(UUID usuarioId, Instant ahora) {
        jdbc.update("UPDATE emprendimiento SET estado = 'ELIMINADO', updated_at = ? WHERE usuario_id = ? AND estado = 'ACTIVO'",
                Timestamp.from(ahora), usuarioId);
    }

    @Override
    public List<UsuarioResumen> listar(Filtro filtro, int page, int size, String sortCampo, boolean ascendente) {
        List<Object> args = new ArrayList<>();
        String where = where(filtro, args);
        String campo = "nombre".equals(sortCampo) ? "lower(nombre)" : "created_at";
        String direccion = ascendente ? "ASC" : "DESC";
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("SELECT id, nombre, rol, estado, created_at FROM usuario" + where
                        + " ORDER BY " + campo + " " + direccion + ", id " + direccion + " LIMIT ? OFFSET ?",
                (rs, i) -> new UsuarioResumen(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        rs.getString("rol"), rs.getString("estado"), rs.getTimestamp("created_at").toInstant()),
                args.toArray());
    }

    @Override
    public long contar(Filtro filtro) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM usuario" + where(filtro, args), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static String where(Filtro filtro, List<Object> args) {
        List<String> condiciones = new ArrayList<>();
        if (!filtro.roles().isEmpty()) {
            condiciones.add("rol IN (" + String.join(", ", filtro.roles().stream().map(r -> "?").toList()) + ")");
            args.addAll(filtro.roles());
        }
        if (filtro.estado() != null) {
            condiciones.add("estado = ?");
            args.add(filtro.estado());
        }
        if (filtro.q() != null) {
            String texto = filtro.q().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            condiciones.add("(lower(nombre) LIKE ? OR celular LIKE ? OR lower(correo) LIKE ?)");
            args.add("%" + texto + "%");
            args.add(texto + "%");
            args.add(texto + "%");
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }
}
