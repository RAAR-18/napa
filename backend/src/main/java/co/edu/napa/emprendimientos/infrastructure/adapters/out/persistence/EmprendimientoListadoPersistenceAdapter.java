package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.emprendimientos.application.ports.out.CampoOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.DireccionOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoListadoProjection;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoListadoRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.out.FiltroEmprendimientos;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EmprendimientoListadoPersistenceAdapter implements EmprendimientoListadoRepositoryPort {
    private static final String FROM_AND_WHERE = """
            FROM emprendimiento e
            JOIN usuario vendedor ON vendedor.id = e.usuario_id
            JOIN ubicacion ubicacion ON ubicacion.id = e.ubicacion_id
            WHERE e.estado = 'ACTIVO'
              AND vendedor.estado = 'ACTIVA'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EmprendimientoListadoPersistenceAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<EmprendimientoListadoProjection> buscar(
            FiltroEmprendimientos filtro,
            long offset,
            int limit
    ) {
        MapSqlParameterSource parameters = filterParameters(filtro)
                .addValue("limit", limit)
                .addValue("offset", offset);
        String distance = distanceExpression(filtro);
        String sql = """
                SELECT e.id,
                       e.nombre,
                       e.categoria,
                       vendedor.rol AS rol_vendedor,
                       e.hora_apertura,
                       e.hora_cierre,
                       ubicacion.latitud,
                       ubicacion.longitud,
                       ubicacion.direccion,
                       %s AS distancia_metros
                %s
                %s
                LIMIT :limit OFFSET :offset
                """.formatted(
                distance,
                FROM_AND_WHERE + filterConditions(filtro),
                orderBy(filtro)
        );
        return jdbcTemplate.query(sql, parameters, (result, rowNumber) ->
                new EmprendimientoListadoProjection(
                        result.getObject("id", java.util.UUID.class),
                        result.getString("nombre"),
                        CategoriaEmprendimiento.valueOf(result.getString("categoria")),
                        tipoVendedor(result.getString("rol_vendedor")),
                        result.getObject("hora_apertura", java.time.LocalTime.class),
                        result.getObject("hora_cierre", java.time.LocalTime.class),
                        result.getDouble("latitud"),
                        result.getDouble("longitud"),
                        result.getString("direccion"),
                        result.getObject("distancia_metros", Double.class)
                )
        );
    }

    @Override
    public long contar(FiltroEmprendimientos filtro) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) " + FROM_AND_WHERE + filterConditions(filtro),
                filterParameters(filtro),
                Long.class
        );
    }

    private MapSqlParameterSource filterParameters(FiltroEmprendimientos filtro) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filtro.tipoVendedor() != null) {
            parameters.addValue(
                    "rol",
                    filtro.tipoVendedor() == TipoVendedor.AMBULANTE
                            ? "VENDEDOR_AMBULANTE"
                            : "VENDEDOR_PUNTO_FIJO"
            );
        }
        if (filtro.tieneCoordenadas()) {
            parameters.addValue("latitude", filtro.latitud());
            parameters.addValue("longitude", filtro.longitud());
        }
        if (filtro.radioMetros() != null) {
            parameters.addValue("radius", filtro.radioMetros());
        }
        return parameters;
    }

    private String filterConditions(FiltroEmprendimientos filtro) {
        StringBuilder conditions = new StringBuilder();
        if (filtro.tipoVendedor() != null) {
            conditions.append(" AND vendedor.rol = :rol");
        }
        if (filtro.radioMetros() != null) {
            conditions.append("""
                     AND ST_DWithin(
                         ubicacion.punto,
                         ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                         :radius
                     )
                    """);
        }
        return conditions.toString();
    }

    private String distanceExpression(FiltroEmprendimientos filtro) {
        if (!filtro.tieneCoordenadas()) {
            return "NULL::double precision";
        }
        return """
                ST_Distance(
                    ubicacion.punto,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                )
                """;
    }

    private String orderBy(FiltroEmprendimientos filtro) {
        if (filtro.ordenarPorCercania()) {
            return """
                    ORDER BY ST_Distance(
                        ubicacion.punto,
                        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                    ) ASC, e.created_at DESC, e.id
                    """;
        }
        String field = filtro.campoOrden() == CampoOrdenEmprendimientos.NOMBRE
                ? "e.nombre"
                : "e.created_at";
        String direction = filtro.direccionOrden() == DireccionOrdenEmprendimientos.ASC ? "ASC" : "DESC";
        return "ORDER BY " + field + " " + direction + ", e.id";
    }

    private TipoVendedor tipoVendedor(String role) {
        return switch (role) {
            case "VENDEDOR_AMBULANTE" -> TipoVendedor.AMBULANTE;
            case "VENDEDOR_PUNTO_FIJO" -> TipoVendedor.PUNTO_FIJO;
            default -> throw new IllegalStateException("Active business belongs to a non-vendor account");
        };
    }
}
