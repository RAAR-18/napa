package co.edu.napa.cuentas;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-08, RF-11 y RF-66 a RF-69 contra PostgreSQL y Redis reales. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("otp-console")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "napa.auth.jwt-secret=users-admin-integration-secret-at-least-32-bytes",
        "napa.pagos.cifrado.clave-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "napa.auth.admin-bootstrap.enabled=true",
        "napa.auth.admin-bootstrap.name=Admin Usuarios",
        "napa.auth.admin-bootstrap.phone=+573004445566",
        "napa.auth.admin-bootstrap.email=usuarios-admin@example.test",
        "napa.auth.admin-bootstrap.password=StrongPassword123",
        "napa.outbox.enabled=false",
        "napa.fotos.directorio=build/test-fotos",
        "NAPA_OTP_CONSOLE_CODE=654321",
        "napa.verification.console-code=654321"
})
class UsuariosIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @Test
    void perfilPropioYEliminacionDeCuenta() throws Exception {
        Usuario ana = registrar("CLIENTE", "Ana Pérez");

        HttpResponse<String> perfil = enviar("GET", "/usuarios/me", ana.token(), null);
        assertEquals(200, perfil.statusCode(), perfil.body());
        assertEquals("Ana Pérez", body(perfil).get("nombre"));
        assertNull(body(perfil).get("ubicacion"));
        assertTrue(perfil.headers().firstValue("Cache-Control").orElse("").contains("no-store"));

        HttpResponse<String> editado = enviar("PATCH", "/usuarios/me", ana.token(), Map.of(
                "nombre", "Ana María Pérez",
                "ubicacion", Map.of("latitud", 11.2408, "longitud", -74.211, "direccion", "Calle 15 # 3-20")));
        assertEquals(200, editado.statusCode(), editado.body());
        assertEquals("Ana María Pérez", body(editado).get("nombre"));
        assertEquals("Calle 15 # 3-20", ((Map<?, ?>) body(editado).get("ubicacion")).get("direccion"));
        HttpResponse<String> otraVez = enviar("PATCH", "/usuarios/me", ana.token(), Map.of(
                "ubicacion", Map.of("latitud", 11.25, "longitud", -74.2, "direccion", "Carrera 5")));
        assertEquals(200, otraVez.statusCode(), otraVez.body());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion u JOIN usuario us ON us.ubicacion_id = u.id"
                + " WHERE us.id = ?", Integer.class, ana.id()));
        assertCodigo(400, "VALIDATION_ERROR", enviar("PATCH", "/usuarios/me", ana.token(), Map.of("rol", "ADMINISTRADOR")));
        assertCodigo(400, "VALIDATION_ERROR", enviar("PATCH", "/usuarios/me", ana.token(), Map.of()));

        // RF-11: un pedido pendiente impide eliminar; al cerrarlo, la cuenta se elimina y el token deja de servir.
        UUID pedido = pedidoPendiente(ana.id());
        HttpResponse<String> bloqueada = enviar("POST", "/usuarios/me/eliminar", ana.token(), Map.of("confirmar", true));
        assertCodigo(409, "HAS_PENDING_OPERATIONS", bloqueada);
        assertEquals(1, ((Number) ((Map<?, ?>) body(bloqueada).get("details")).get("pedidosPendientes")).intValue());
        jdbc.update("UPDATE pedido SET estado = 'ENTREGADO' WHERE id = ?", pedido);
        assertCodigo(400, "VALIDATION_ERROR", enviar("POST", "/usuarios/me/eliminar", ana.token(), Map.of()));
        HttpResponse<String> eliminada = enviar("POST", "/usuarios/me/eliminar", ana.token(), Map.of("confirmar", true));
        assertEquals(204, eliminada.statusCode(), eliminada.body());
        assertEquals("ELIMINADA", jdbc.queryForObject("SELECT estado FROM usuario WHERE id = ?", String.class, ana.id()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_type = 'cuentas.cuenta-eliminada'"
                + " AND aggregate_id = ?", Integer.class, ana.id()));
        assertEquals(401, enviar("GET", "/usuarios/me", ana.token(), null).statusCode());
    }

    @Test
    void cambioDeCelularConCodigoYFotoDePerfil() throws Exception {
        Usuario luis = registrar("DOMICILIARIO", "Luis Díaz");
        String anterior = jdbc.queryForObject("SELECT celular FROM usuario WHERE id = ?", String.class, luis.id());
        String nuevo = "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);

        HttpResponse<String> solicitud = enviar("POST", "/usuarios/me/celular/solicitar-cambio", luis.token(),
                Map.of("celular", nuevo));
        assertEquals(202, solicitud.statusCode(), solicitud.body());
        assertEquals(anterior, jdbc.queryForObject("SELECT celular FROM usuario WHERE id = ?", String.class, luis.id()));
        assertCodigo(400, "OTP_INVALID", enviar("POST", "/usuarios/me/celular/confirmar", luis.token(),
                Map.of("codigo", "111111")));
        HttpResponse<String> confirmado = enviar("POST", "/usuarios/me/celular/confirmar", luis.token(),
                Map.of("codigo", "654321"));
        assertEquals(200, confirmado.statusCode(), confirmado.body());
        assertEquals(nuevo, body(confirmado).get("celular"));
        assertCodigo(409, "PHONE_ALREADY_REGISTERED", enviar("POST", "/usuarios/me/celular/solicitar-cambio",
                registrar("CLIENTE", "Otra").token(), Map.of("celular", nuevo)));

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        HttpResponse<String> foto = subirFoto(luis.token(), png);
        assertEquals(200, foto.statusCode(), foto.body());
        String url = body(foto).get("fotoUrl").toString();
        assertTrue(url.startsWith("/media/perfiles/") && url.endsWith(".png"), url);
        HttpResponse<byte[]> servida = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, servida.statusCode());
        assertEquals(png.length, servida.body().length);
        assertCodigo(400, "VALIDATION_ERROR", subirFoto(luis.token(), "GIF89a no soportado".getBytes()));
    }

    private HttpResponse<String> subirFoto(String token, byte[] contenido) throws Exception {
        String limite = "----napa" + UUID.randomUUID();
        byte[] inicio = ("--" + limite + "\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"f.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes();
        byte[] fin = ("\r\n--" + limite + "--\r\n").getBytes();
        byte[] cuerpo = new byte[inicio.length + contenido.length + fin.length];
        System.arraycopy(inicio, 0, cuerpo, 0, inicio.length);
        System.arraycopy(contenido, 0, cuerpo, inicio.length, contenido.length);
        System.arraycopy(fin, 0, cuerpo, inicio.length + contenido.length, fin.length);
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/usuarios/me/foto"))
                .header("Authorization", "Bearer " + token)
                .header("X-Device-Id", "usuarios-test")
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(cuerpo))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void administracionDeUsuarios() throws Exception {
        String admin = adminToken();
        Usuario carlos = registrar("CLIENTE", "Carlos Gómez");
        Usuario vendedor = registrar("VENDEDOR_AMBULANTE", "Vendedor Uno");

        // RF-69
        Map<String, Object> clientes = body(enviar("GET", "/usuarios?rol=CLIENTE&q=carl", admin, null));
        assertEquals(1, ((Number) clientes.get("totalElements")).intValue());
        assertEquals("Carlos Gómez", lista(clientes).getFirst().get("nombre"));
        Map<String, Object> vendedores = body(enviar("GET", "/usuarios?rol=VENDEDOR", admin, null));
        assertTrue(lista(vendedores).stream().allMatch(u -> u.get("rol").toString().startsWith("VENDEDOR")));
        assertCodigo(400, "VALIDATION_ERROR", enviar("GET", "/usuarios?q=a", admin, null));
        assertCodigo(400, "VALIDATION_ERROR", enviar("GET", "/usuarios?otro=1", admin, null));
        assertCodigo(403, "FORBIDDEN", enviar("GET", "/usuarios", carlos.token(), null));

        // RF-66
        HttpResponse<String> detalle = enviar("GET", "/usuarios/" + carlos.id(), admin, null);
        assertEquals(200, detalle.statusCode(), detalle.body());
        assertEquals("CLIENTE", body(detalle).get("rol"));
        assertCodigo(404, "NOT_FOUND", enviar("GET", "/usuarios/" + UUID.randomUUID(), admin, null));

        // RF-67: cambio de rol cierra sesiones; celular de otra cuenta, 409.
        HttpResponse<String> editado = enviar("PATCH", "/usuarios/" + carlos.id(), admin, Map.of(
                "rol", "DOMICILIARIO", "correo", "Carlos@Example.test"));
        assertEquals(200, editado.statusCode(), editado.body());
        assertEquals("DOMICILIARIO", body(editado).get("rol"));
        assertEquals("carlos@example.test", body(editado).get("correo"));
        assertEquals(401, enviar("GET", "/usuarios/me", carlos.token(), null).statusCode());
        String celularVendedor = jdbc.queryForObject("SELECT celular FROM usuario WHERE id = ?", String.class, vendedor.id());
        assertCodigo(409, "PHONE_ALREADY_REGISTERED", enviar("PATCH", "/usuarios/" + carlos.id(), admin,
                Map.of("celular", celularVendedor)));
        UUID adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE rol = 'ADMINISTRADOR'", UUID.class);
        assertCodigo(422, "BUSINESS_RULE", enviar("PATCH", "/usuarios/" + adminId, admin, Map.of("nombre", "Otro")));

        // RF-68: con emprendimiento activo hay advertencia; con forzar se elimina también el emprendimiento.
        UUID emprendimiento = emprendimiento(vendedor.id());
        HttpResponse<String> advertencia = enviar("POST", "/usuarios/" + vendedor.id() + "/eliminar", admin,
                Map.of("confirmar", true));
        assertCodigo(409, "HAS_PENDING_OPERATIONS", advertencia);
        assertEquals(Boolean.TRUE, ((Map<?, ?>) body(advertencia).get("details")).get("emprendimientoActivo"));
        HttpResponse<String> forzada = enviar("POST", "/usuarios/" + vendedor.id() + "/eliminar", admin,
                Map.of("confirmar", true, "forzar", true));
        assertEquals(204, forzada.statusCode(), forzada.body());
        assertEquals("ELIMINADO", jdbc.queryForObject("SELECT estado FROM emprendimiento WHERE id = ?",
                String.class, emprendimiento));
        assertCodigo(409, "INVALID_STATE", enviar("POST", "/usuarios/" + vendedor.id() + "/eliminar", admin,
                Map.of("confirmar", true)));
        assertCodigo(422, "BUSINESS_RULE", enviar("POST", "/usuarios/" + adminId + "/eliminar", admin,
                Map.of("confirmar", true)));
    }

    private UUID pedidoPendiente(UUID clienteId) {
        Usuario vendedor;
        try {
            vendedor = registrar("VENDEDOR_AMBULANTE", "Vendedor Pedido");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        UUID emprendimiento = emprendimiento(vendedor.id());
        UUID pedido = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO pedido (id, usuario_id, emprendimiento_id, modalidad, estado, medio_pago, total,
                                    created_at, updated_at)
                VALUES (?, ?, ?, 'RECOGIDA', 'PENDIENTE', 'EFECTIVO', 5000, ?, ?)
                """, pedido, clienteId, emprendimiento, ahora, ahora);
        return pedido;
    }

    private UUID emprendimiento(UUID vendedorId) {
        Timestamp ahora = Timestamp.from(Instant.now());
        UUID ubicacion = UUID.randomUUID();
        jdbc.update("INSERT INTO ubicacion (id, latitud, longitud, direccion, created_at, updated_at) VALUES (?, 10.4, -75.5, 'Calle 1', ?, ?)",
                ubicacion, ahora, ahora);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO emprendimiento (id, usuario_id, ubicacion_id, nombre, categoria, descripcion,
                                            hora_apertura, hora_cierre, estado, created_at, updated_at)
                VALUES (?, ?, ?, 'Puesto', 'OTROS', 'Puesto de prueba', '08:00', '18:00', 'ACTIVO', ?, ?)
                """, id, vendedorId, ubicacion, ahora, ahora);
        return id;
    }

    private Usuario registrar(String rol, String nombre) throws Exception {
        String celular = "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);
        HttpResponse<String> registro = enviar("POST", "/auth/register", null, Map.of(
                "nombre", nombre, "celular", celular, "password", "initial-password",
                "confirmPassword", "initial-password", "rol", rol, "canal", "SMS"));
        assertEquals(201, registro.statusCode(), registro.body());
        HttpResponse<String> verificado = enviar("POST", "/auth/verify-phone/confirm", null,
                Map.of("celular", celular, "codigo", "654321"));
        assertEquals(200, verificado.statusCode(), verificado.body());
        UUID id = jdbc.queryForObject("SELECT id FROM usuario WHERE celular = ?", UUID.class, celular);
        return new Usuario(id, body(verificado).get("accessToken").toString());
    }

    private String adminToken() throws Exception {
        HttpResponse<String> login = enviar("POST", "/auth/login", null, Map.of(
                "identificador", "usuarios-admin@example.test", "password", "StrongPassword123"));
        assertEquals(200, login.statusCode(), login.body());
        return body(login).get("accessToken").toString();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + ruta))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", "usuarios-test");
        if ("POST".equals(metodo)) {
            builder.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        String texto = cuerpo == null ? "" : json.writeValueAsString(cuerpo);
        builder.method(metodo, texto.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(texto));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertCodigo(int status, String codigo, HttpResponse<String> respuesta) throws Exception {
        assertEquals(status, respuesta.statusCode(), respuesta.body());
        assertEquals(codigo, body(respuesta).get("code"));
    }

    private Map<String, Object> body(HttpResponse<String> respuesta) throws Exception {
        return json.readValue(respuesta.body(), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Map<String, Object> pagina) {
        return (List<Map<String, Object>>) pagina.get("content");
    }

    private record Usuario(UUID id, String token) {}
}
