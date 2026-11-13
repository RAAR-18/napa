package co.edu.napa.pedidos.infrastructure.adapters.out.persistence;

import co.edu.napa.shared.domain.geo.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class DistanciaLineaRectaPersistenceAdapterIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @Test
    void calculatesGeodesicDistanceUsingPostgisGeography() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()
        ));
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS postgis");

        DistanciaLineaRectaPersistenceAdapter adapter = new DistanciaLineaRectaPersistenceAdapter(jdbc);
        long roundedMeters = adapter.distanciaMetros(
                        new GeoPoint(0, 0),
                        new GeoPoint(0, 1)
                )
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        assertEquals(111_319, roundedMeters);
    }
}
