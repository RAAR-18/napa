package co.edu.napa.pedidos.infrastructure.adapters.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PedidoCantidadMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));
    @Test
    void migrationAllowsMoreThanTwentyAndKeepsPositiveQuantities() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        // LIKE copia los CHECK y NOT NULL, sin claves foráneas a datos de negocio.
        jdbc.execute("CREATE TABLE cantidad_prueba (LIKE item_pedido INCLUDING CONSTRAINTS)");
        String sql = "INSERT INTO cantidad_prueba(id,pedido_id,producto_id,cantidad,precio_unitario) VALUES (?,?,?,?,?)";
        jdbc.update(sql, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25.50"), 1000L);
        assertEquals(new BigDecimal("25.50"), jdbc.queryForObject("SELECT cantidad FROM cantidad_prueba", BigDecimal.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, 1000L));
    }
}
