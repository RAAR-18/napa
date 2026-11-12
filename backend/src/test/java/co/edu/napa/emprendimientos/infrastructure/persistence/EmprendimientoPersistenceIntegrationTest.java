package co.edu.napa.emprendimientos.infrastructure.persistence;

import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosPort;
import co.edu.napa.emprendimientos.application.ports.out.CampoOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.DireccionOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.FiltroEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosQuery;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class EmprendimientoPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("napa.outbox.enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmprendimientoRepositoryPort emprendimientos;

    @Autowired
    private ListarEmprendimientosPort listarEmprendimientos;

    @Autowired
    private UbicacionRepositoryPort ubicaciones;

    @Test
    void migrationAllowsAmbulantLocationsWithoutAddressAndPersistsAnActiveBusiness() {
        UUID userId = insertActiveSeller();
        Instant now = Instant.now();
        Ubicacion location = ubicaciones.save(new Ubicacion(
                UUID.randomUUID(),
                11.2408,
                -74.211,
                null,
                "Frente al parque",
                now,
                now
        ));

        Emprendimiento saved = emprendimientos.save(business(userId, location.id()));

        assertEquals(EstadoEmprendimiento.ACTIVO, saved.estado());
        assertTrue(emprendimientos.existsActiveByUsuarioId(userId));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'ubicacion'
                          AND column_name = 'direccion'
                          AND is_nullable = 'YES'
                        """,
                        Integer.class
                )
        );
        assertEquals(
                "ACTIVO",
                jdbcTemplate.queryForObject(
                        "SELECT estado FROM emprendimiento WHERE id = ?",
                        String.class,
                        saved.id()
                )
        );
    }

    @Test
    void uniqueActiveBusinessIndexMapsSecondActiveBusinessToDomainConflict() {
        UUID userId = insertActiveSeller();
        Instant now = Instant.now();
        Ubicacion firstLocation = saveLocation(now);
        Ubicacion secondLocation = saveLocation(now);
        emprendimientos.save(business(userId, firstLocation.id()));

        DomainException exception = assertThrows(
                DomainException.class,
                () -> emprendimientos.save(business(userId, secondLocation.id()))
        );

        assertEquals("CONFLICT", exception.code());
    }

    @Test
    void uniqueActiveBusinessIndexAllowsOnlyOneConcurrentCreation() throws Exception {
        UUID userId = insertActiveSeller();
        Instant now = Instant.now();
        Ubicacion firstLocation = saveLocation(now);
        Ubicacion secondLocation = saveLocation(now);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> tryCreate(userId, firstLocation, ready, start));
            Future<Boolean> second = executor.submit(() -> tryCreate(userId, secondLocation, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertTrue(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void listingExcludesInactiveAccountsAndPaginatesActiveBusinesses() {
        UUID visibleUser = insertActiveSeller(RolUsuario.VENDEDOR_PUNTO_FIJO, EstadoUsuario.ACTIVA);
        UUID hiddenUser = insertActiveSeller(
                RolUsuario.VENDEDOR_PUNTO_FIJO,
                EstadoUsuario.PENDIENTE_VERIFICACION
        );
        UUID inactiveBusinessUser = insertActiveSeller(
                RolUsuario.VENDEDOR_PUNTO_FIJO,
                EstadoUsuario.ACTIVA
        );
        UUID deletedBusinessUser = insertActiveSeller(
                RolUsuario.VENDEDOR_PUNTO_FIJO,
                EstadoUsuario.ACTIVA
        );
        Ubicacion visibleLocation = saveLocation(Instant.now(), 11.2408, -74.211);
        Ubicacion hiddenLocation = saveLocation(Instant.now(), 11.241, -74.211);
        Ubicacion inactiveBusinessLocation = saveLocation(Instant.now(), 11.242, -74.211);
        Ubicacion deletedBusinessLocation = saveLocation(Instant.now(), 11.243, -74.211);
        emprendimientos.save(business(visibleUser, visibleLocation.id(), "Visible"));
        emprendimientos.save(business(hiddenUser, hiddenLocation.id(), "Oculto"));
        emprendimientos.save(business(
                inactiveBusinessUser,
                inactiveBusinessLocation.id(),
                "Inactivo",
                EstadoEmprendimiento.INACTIVO
        ));
        emprendimientos.save(business(
                deletedBusinessUser,
                deletedBusinessLocation.id(),
                "Eliminado",
                EstadoEmprendimiento.ELIMINADO
        ));
        FiltroEmprendimientos filter = filter(TipoVendedor.PUNTO_FIJO, null, null, null, false);

        var firstPage = listarEmprendimientos.listar(new ListarEmprendimientosQuery(filter, 0, 1));
        var secondPage = listarEmprendimientos.listar(new ListarEmprendimientosQuery(filter, 1, 1));

        assertEquals(1, firstPage.totalElements());
        assertEquals(1, firstPage.content().size());
        assertEquals("Visible", firstPage.content().getFirst().nombre());
        assertEquals(0, secondPage.content().size());
        assertEquals(1, secondPage.totalElements());
    }

    @Test
    void filtersByPostgisRadiusAndOrdersByDistance() {
        UUID nearUser = insertActiveSeller(RolUsuario.VENDEDOR_PUNTO_FIJO, EstadoUsuario.ACTIVA);
        UUID farUser = insertActiveSeller(RolUsuario.VENDEDOR_PUNTO_FIJO, EstadoUsuario.ACTIVA);
        Ubicacion nearLocation = saveLocation(Instant.now(), 4.711, -74.0721);
        Ubicacion farLocation = saveLocation(Instant.now(), 4.8, -74.1);
        emprendimientos.save(business(nearUser, nearLocation.id(), "Cercano"));
        emprendimientos.save(business(farUser, farLocation.id(), "Lejano"));
        FiltroEmprendimientos filter = filter(
                TipoVendedor.PUNTO_FIJO,
                4.711,
                -74.0721,
                2_000,
                true
        );

        var page = listarEmprendimientos.listar(new ListarEmprendimientosQuery(filter, 0, 20));

        assertEquals(1, page.totalElements());
        assertEquals(1, page.content().size());
        assertEquals("Cercano", page.content().getFirst().nombre());
        assertTrue(page.content().getFirst().distanciaMetros() < 1);
    }

    private boolean tryCreate(
            UUID userId,
            Ubicacion location,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start.");
        }
        try {
            emprendimientos.save(business(userId, location.id()));
            return true;
        } catch (DomainException exception) {
            if ("CONFLICT".equals(exception.code())) {
                return false;
            }
            throw exception;
        }
    }

    private UUID insertActiveSeller() {
        return insertActiveSeller(RolUsuario.VENDEDOR_AMBULANTE, EstadoUsuario.ACTIVA);
    }

    private UUID insertActiveSeller(RolUsuario role, EstadoUsuario status) {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String celular = "+573" + String.format(
                "%09d",
                ThreadLocalRandom.current().nextInt(1_000_000_000)
        );
        jdbcTemplate.update(
                """
                INSERT INTO usuario (
                    id, nombre, celular, password_hash, rol, estado,
                    celular_verificado_at, created_at, updated_at
                ) VALUES (?, 'Vendedor', ?, 'hash', ?, ?, ?, ?, ?)
                """,
                userId,
                celular,
                role.name(),
                status.name(),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return userId;
    }

    private Ubicacion saveLocation(Instant now) {
        return saveLocation(now, 11.2408, -74.211);
    }

    private Ubicacion saveLocation(Instant now, double latitude, double longitude) {
        return ubicaciones.save(new Ubicacion(
                UUID.randomUUID(),
                latitude,
                longitude,
                null,
                "Zona de venta",
                now,
                now
        ));
    }

    private Emprendimiento business(UUID userId, UUID locationId) {
        return business(userId, locationId, "Frutas Doña Rosa");
    }

    private Emprendimiento business(UUID userId, UUID locationId, String name) {
        return business(userId, locationId, name, EstadoEmprendimiento.ACTIVO);
    }

    private Emprendimiento business(
            UUID userId,
            UUID locationId,
            String name,
            EstadoEmprendimiento state
    ) {
        Instant now = Instant.now();
        return new Emprendimiento(
                UUID.randomUUID(),
                userId,
                locationId,
                name,
                CategoriaEmprendimiento.FRUTAS_VERDURAS,
                "Frutas frescas del día",
                LocalTime.of(6, 0),
                LocalTime.of(18, 0),
                state,
                now,
                now
        );
    }

    private FiltroEmprendimientos filter(
            TipoVendedor sellerType,
            Double latitude,
            Double longitude,
            Integer radius,
            boolean nearest
    ) {
        return new FiltroEmprendimientos(
                sellerType,
                latitude,
                longitude,
                radius,
                nearest,
                CampoOrdenEmprendimientos.CREATED_AT,
                DireccionOrdenEmprendimientos.DESC
        );
    }
}
