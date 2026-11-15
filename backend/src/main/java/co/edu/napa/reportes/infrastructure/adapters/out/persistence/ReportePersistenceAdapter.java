package co.edu.napa.reportes.infrastructure.adapters.out.persistence;

import co.edu.napa.reportes.application.ports.out.ReporteRepositoryPort;
import co.edu.napa.reportes.domain.model.EstadoReporte;
import co.edu.napa.reportes.domain.model.MotivoReporte;
import co.edu.napa.reportes.domain.model.Reporte;
import co.edu.napa.reportes.domain.model.TipoEntidadReportada;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReportePersistenceAdapter implements ReporteRepositoryPort {
    private static final String COLUMNAS = """
            id, autor_id, motivo, descripcion, pedido_id, domicilio_id, usuario_reportado_id, estado,
            nota_resolucion, resuelto_por_id, created_at, updated_at
            """;

    private static final RowMapper<Reporte> MAPPER = (rs, i) -> {
        UUID pedido = rs.getObject("pedido_id", UUID.class);
        UUID domicilio = rs.getObject("domicilio_id", UUID.class);
        UUID usuario = rs.getObject("usuario_reportado_id", UUID.class);
        TipoEntidadReportada tipo = pedido != null ? TipoEntidadReportada.PEDIDO
                : domicilio != null ? TipoEntidadReportada.DOMICILIO
                : usuario != null ? TipoEntidadReportada.USUARIO
                : null;
        UUID entidad = pedido != null ? pedido : domicilio != null ? domicilio : usuario;
        return new Reporte(
                rs.getObject("id", UUID.class),
                rs.getObject("autor_id", UUID.class),
                MotivoReporte.valueOf(rs.getString("motivo")),
                rs.getString("descripcion"),
                tipo,
                entidad,
                EstadoReporte.valueOf(rs.getString("estado")),
                rs.getString("nota_resolucion"),
                rs.getObject("resuelto_por_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    };

    private final JdbcTemplate jdbc;

    public ReportePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(Reporte r) {
        jdbc.update("""
                INSERT INTO reporte (id, autor_id, motivo, descripcion, pedido_id, domicilio_id, usuario_reportado_id,
                                     estado, nota_resolucion, resuelto_por_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                r.id(), r.autorId(), r.motivo().name(), r.descripcion(),
                r.tipoEntidad() == TipoEntidadReportada.PEDIDO ? r.entidadId() : null,
                r.tipoEntidad() == TipoEntidadReportada.DOMICILIO ? r.entidadId() : null,
                r.tipoEntidad() == TipoEntidadReportada.USUARIO ? r.entidadId() : null,
                r.estado().name(), r.notaResolucion(), r.resueltoPorId(),
                Timestamp.from(r.createdAt()), Timestamp.from(r.updatedAt()));
    }

    @Override
    public boolean existeAbierto(UUID autorId, MotivoReporte motivo, TipoEntidadReportada tipoEntidad, UUID entidadId) {
        String entidad = tipoEntidad == null
                ? "pedido_id IS NULL AND domicilio_id IS NULL AND usuario_reportado_id IS NULL"
                : columna(tipoEntidad) + " = ?";
        List<Object> args = new ArrayList<>(List.of(autorId, motivo.name()));
        if (tipoEntidad != null) {
            args.add(entidadId);
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM reporte WHERE autor_id = ? AND motivo = ? AND estado <> 'RESUELTO' AND "
                        + entidad + ")",
                Boolean.class, args.toArray()));
    }

    @Override
    public Optional<Reporte> buscar(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM reporte WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Reporte> buscarParaActualizar(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM reporte WHERE id = ? FOR UPDATE", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public void actualizar(Reporte r) {
        jdbc.update("""
                UPDATE reporte SET estado = ?, nota_resolucion = ?, resuelto_por_id = ?, updated_at = ?
                WHERE id = ?
                """,
                r.estado().name(), r.notaResolucion(), r.resueltoPorId(), Timestamp.from(r.updatedAt()), r.id());
    }

    @Override
    public List<Reporte> listar(UUID autorId, EstadoReporte estado, int page, int size, boolean ascendente) {
        List<Object> args = new ArrayList<>();
        String where = where(autorId, estado, args);
        String direccion = ascendente ? "ASC" : "DESC";
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("SELECT " + COLUMNAS + " FROM reporte" + where
                        + " ORDER BY created_at " + direccion + ", id " + direccion + " LIMIT ? OFFSET ?",
                MAPPER, args.toArray());
    }

    @Override
    public long contar(UUID autorId, EstadoReporte estado) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM reporte" + where(autorId, estado, args),
                Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static String where(UUID autorId, EstadoReporte estado, List<Object> args) {
        List<String> condiciones = new ArrayList<>();
        if (autorId != null) {
            condiciones.add("autor_id = ?");
            args.add(autorId);
        }
        if (estado != null) {
            condiciones.add("estado = ?");
            args.add(estado.name());
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    private static String columna(TipoEntidadReportada tipo) {
        return switch (tipo) {
            case PEDIDO -> "pedido_id";
            case DOMICILIO -> "domicilio_id";
            case USUARIO -> "usuario_reportado_id";
        };
    }
}
