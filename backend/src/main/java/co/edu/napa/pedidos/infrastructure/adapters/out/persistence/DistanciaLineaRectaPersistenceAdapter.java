package co.edu.napa.pedidos.infrastructure.adapters.out.persistence;

import co.edu.napa.pedidos.application.ports.out.DistanciaLineaRectaPort;
import co.edu.napa.shared.domain.geo.GeoPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Objects;

@Repository
public class DistanciaLineaRectaPersistenceAdapter implements DistanciaLineaRectaPort {
    private static final String DISTANCE_SQL = """
            SELECT ST_Distance(
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
            )
            """;

    private final JdbcTemplate jdbc;

    public DistanciaLineaRectaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public BigDecimal distanciaMetros(GeoPoint origen, GeoPoint destino) {
        Double metros = jdbc.queryForObject(
                DISTANCE_SQL,
                Double.class,
                origen.longitude(), origen.latitude(),
                destino.longitude(), destino.latitude()
        );
        return BigDecimal.valueOf(Objects.requireNonNull(metros, "PostGIS returned no distance"));
    }
}
