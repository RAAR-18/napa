package co.edu.napa.calificaciones;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-01 a RF-06 y RF-60 a RF-62 contra PostgreSQL y Redis reales. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("otp-console")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/application.yml",
        "napa.auth.jwt-secret=ratings-integration-secret-at-least-32-bytes",
        "napa.pagos.cifrado.clave-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "napa.auth.admin-bootstrap.enabled=true",
        "napa.auth.admin-bootstrap.name=Admin Calificaciones",
        "napa.auth.admin-bootstrap.phone=+573001112233",
        "napa.auth.admin-bootstrap.email=calificaciones-admin@example.test",
        "napa.auth.admin-bootstrap.password=StrongPassword123",
        "napa.outbox.enabled=false",
        "NAPA_OTP_CONSOLE_CODE=654321",
        "napa.verification.console-code=654321"
})
class CalificacionesYReportesIntegrationTest {
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

    private Usuario cliente;
    private Usuario vendedor;
    private Usuario domiciliario;
    private UUID emprendimiento;
    private UUID producto;
    private UUID pedido;
    private UUID domicilio;

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

    @BeforeEach
    void escenario() throws Exception {
        cliente = registrar("CLIENTE");
        vendedor = registrar("VENDEDOR_PUNTO_FIJO");
        domiciliario = registrar("DOMICILIARIO");
        Timestamp ahora = Timestamp.from(Instant.now());
        emprendimiento = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO emprendimiento (id, usuario_id, ubicacion_id, nombre, categoria, descripcion,
                                            hora_apertura, hora_cierre, estado, created_at, updated_at)
                VALUES (?, ?, ?, 'Frutas Ana', 'FRUTAS_VERDURAS', 'Frutas frescas', '08:00', '18:00', 'ACTIVO', ?, ?)
                """, emprendimiento, vendedor.id(), ubicacion(), ahora, ahora);
        producto = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO producto (id, emprendimiento_id, nombre, precio, unidad, cantidad_disponible, estado,
                                      created_at, updated_at)
                VALUES (?, ?, 'Mango', 3000, 'UNIDAD', 10, 'DISPONIBLE', ?, ?)
                """, producto, emprendimiento, ahora, ahora);
        pedido = pedido("ENTREGADO");
        domicilio = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO domicilio (id, pedido_id, vendedor_id, comprador_id, domiciliario_id, estado, tarifa,
                                       ubicacion_origen_id, ubicacion_destino_id, codigo_confirmacion, asignado_at,
                                       created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ENTREGADO', 5000, ?, ?, '123456', ?, ?, ?)
                """, domicilio, pedido, vendedor.id(), cliente.id(), domiciliario.id(), ubicacion(), ubicacion(),
                ahora, ahora, ahora);
    }

    @Test
    void ciclo_completo_de_calificaciones() throws Exception {
        // Opciones: el cliente puede calificar emprendimiento, producto y domiciliario; el domiciliario llega por domicilio.
        Map<String, Object> opciones = body(enviar("GET", "/calificaciones/opciones?pedidoId=" + pedido, cliente.token(), null));
        assertEquals(3, ((List<?>) opciones.get("opciones")).size());
        Map<String, Object> delDomiciliario = body(enviar("GET", "/calificaciones/opciones?domicilioId=" + domicilio,
                domiciliario.token(), null));
        assertEquals(pedido.toString(), delDomiciliario.get("pedidoId"));
        assertEquals(2, ((List<?>) delDomiciliario.get("opciones")).size());

        // RF-01: cliente califica emprendimiento, producto y domiciliario.
        HttpResponse<String> creada = calificar(cliente, "EMPRENDIMIENTO", emprendimiento, 5, "Muy frescas");
        assertEquals(201, creada.statusCode(), creada.body());
        assertEquals("ACTIVA", body(creada).get("estado"));
        String calificacionId = body(creada).get("id").toString();
        assertEquals(201, calificar(cliente, "PRODUCTO", producto, 4, null).statusCode());
        assertEquals(201, calificar(cliente, "USUARIO", domiciliario.id(), 3, "Algo tarde").statusCode());
        Map<String, Object> yaHechas = body(enviar("GET", "/calificaciones/opciones?pedidoId=" + pedido, cliente.token(), null));
        assertTrue(contenidoDe(yaHechas).stream().allMatch(o -> o.get("calificacionId") != null));
        // Vendedor y domiciliario califican a sus contrapartes.
        assertEquals(201, calificar(vendedor, "USUARIO", cliente.id(), 5, null).statusCode());
        assertEquals(201, calificar(vendedor, "USUARIO", domiciliario.id(), 4, null).statusCode());
        assertEquals(201, calificar(domiciliario, "EMPRENDIMIENTO", emprendimiento, 2, "Me hicieron esperar").statusCode());

        // Repetida, fuera de escala, ajena, pedido no entregado.
        assertCodigo(409, "CONFLICT", calificar(cliente, "EMPRENDIMIENTO", emprendimiento, 1, null));
        assertCodigo(400, "VALIDATION_ERROR", calificar(cliente, "PRODUCTO", producto, 6, null));
        assertCodigo(404, "NOT_FOUND", calificar(domiciliario, "PRODUCTO", producto, 3, null));
        UUID pendiente = pedido("PENDIENTE");
        HttpResponse<String> noEntregado = enviar("POST", "/calificaciones", cliente.token(), Map.of(
                "pedidoId", pendiente, "tipoEntidad", "EMPRENDIMIENTO", "entidadId", emprendimiento, "puntaje", 4));
        assertCodigo(409, "INVALID_STATE", noEntregado);
        assertEquals("PENDIENTE", detalles(noEntregado).get("estadoActual"));

        // RF-02: promedio (5 + 2) / 2 = 3.5 y "esMia".
        HttpResponse<String> lista = enviar("GET",
                "/calificaciones?tipoEntidad=EMPRENDIMIENTO&entidadId=" + emprendimiento, cliente.token(), null);
        assertEquals(200, lista.statusCode(), lista.body());
        Map<String, Object> pagina = body(lista);
        assertEquals(2, ((Number) pagina.get("totalElements")).intValue());
        assertEquals(3.5, ((Number) resumen(pagina).get("promedio")).doubleValue());
        assertTrue(contenido(pagina).stream().anyMatch(c -> Boolean.TRUE.equals(c.get("esMia"))));
        assertCodigo(400, "VALIDATION_ERROR", enviar("GET", "/calificaciones?estado=ACTIVA&tipoEntidad=EMPRENDIMIENTO&entidadId="
                + emprendimiento, cliente.token(), null));
        Usuario otroVendedor = registrar("VENDEDOR_AMBULANTE");
        assertCodigo(403, "FORBIDDEN", enviar("GET", "/calificaciones?tipoEntidad=EMPRENDIMIENTO&entidadId="
                + emprendimiento, otroVendedor.token(), null));

        // Resúmenes del catálogo: el producto calificado con 4 y uno inexistente que se omite.
        Map<String, Object> resumenes = body(enviar("GET", "/calificaciones/resumenes?tipoEntidad=PRODUCTO&ids="
                + producto + "," + UUID.randomUUID(), cliente.token(), null));
        List<?> listaResumenes = (List<?>) resumenes.get("resumenes");
        assertEquals(1, listaResumenes.size());
        assertEquals(4.0, ((Number) ((Map<?, ?>) listaResumenes.getFirst()).get("promedio")).doubleValue());
        assertTrue(((List<?>) body(enviar("GET", "/calificaciones/resumenes?tipoEntidad=PRODUCTO&ids=" + producto,
                otroVendedor.token(), null)).get("resumenes")).isEmpty());

        // RF-04: el autor edita; otro no puede.
        HttpResponse<String> editada = enviar("PATCH", "/calificaciones/" + calificacionId, cliente.token(),
                Map.of("puntaje", 3));
        assertEquals(200, editada.statusCode(), editada.body());
        assertEquals(3, ((Number) body(editada).get("puntaje")).intValue());
        assertCodigo(403, "FORBIDDEN", enviar("PATCH", "/calificaciones/" + calificacionId, vendedor.token(),
                Map.of("puntaje", 1)));
        Map<String, Object> comentarioNulo = new HashMap<>();
        comentarioNulo.put("comentario", null);
        assertCodigo(400, "VALIDATION_ERROR", enviar("PATCH", "/calificaciones/" + calificacionId,
                cliente.token(), comentarioNulo));

        // RF-06: el administrador modera el texto y elimina; queda registro y evento E17.
        String admin = adminToken();
        HttpResponse<String> moderada = enviar("PATCH", "/calificaciones/" + calificacionId + "/moderar-texto", admin,
                Map.of("comentario", "Frescas", "motivo", "Se ajustó el texto"));
        assertEquals(200, moderada.statusCode(), moderada.body());
        HttpResponse<String> eliminada = enviar("POST", "/calificaciones/" + calificacionId + "/eliminar-moderacion",
                admin, Map.of("motivo", "Lenguaje ofensivo"));
        assertEquals(200, eliminada.statusCode(), eliminada.body());
        assertEquals("ELIMINADA_MODERACION", body(eliminada).get("estado"));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM moderacion_comentario WHERE calificacion_id = ?::uuid", Integer.class, calificacionId));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'calificaciones.comentario-moderado'"
                        + " AND aggregate_id = ?::uuid", Integer.class, calificacionId));
        assertCodigo(409, "INVALID_STATE", enviar("POST", "/calificaciones/" + calificacionId + "/eliminar",
                cliente.token(), null));

        // El promedio se recalcula sin la eliminada; RF-03: el admin la ve filtrando por estado.
        Map<String, Object> despues = body(enviar("GET",
                "/calificaciones?tipoEntidad=EMPRENDIMIENTO&entidadId=" + emprendimiento, cliente.token(), null));
        assertEquals(2.0, ((Number) resumen(despues).get("promedio")).doubleValue());
        Map<String, Object> supervision = body(enviar("GET", "/calificaciones?estado=ELIMINADA_MODERACION", admin, null));
        assertEquals(1, ((Number) supervision.get("totalElements")).intValue());
        Map<String, Object> item = contenido(supervision).getFirst();
        assertEquals(Boolean.TRUE, item.get("moderada"));
        assertEquals("Frutas Ana", ((Map<?, ?>) item.get("entidad")).get("nombre"));
        assertNull(supervision.get("resumen"));

        // RF-05: el domiciliario elimina la suya.
        String suya = jdbc.queryForObject("SELECT id::text FROM calificacion WHERE autor_id = ?",
                String.class, domiciliario.id());
        HttpResponse<String> propia = enviar("POST", "/calificaciones/" + suya + "/eliminar", domiciliario.token(), null);
        assertEquals(200, propia.statusCode(), propia.body());
        assertEquals("ELIMINADA_AUTOR", body(propia).get("estado"));
        Map<String, Object> sinCalificaciones = body(enviar("GET",
                "/calificaciones?tipoEntidad=EMPRENDIMIENTO&entidadId=" + emprendimiento, cliente.token(), null));
        assertNull(resumen(sinCalificaciones).get("promedio"));
    }

    @Test
    void ciclo_completo_de_reportes() throws Exception {
        HttpResponse<String> creado = enviar("POST", "/reportes", domiciliario.token(), Map.of(
                "motivo", "DOMICILIO",
                "descripcion", "Esperé 15 minutos en la dirección y nadie contestó.",
                "domicilioId", domicilio));
        assertEquals(201, creado.statusCode(), creado.body());
        assertEquals("PENDIENTE", body(creado).get("estado"));
        String reporteId = body(creado).get("id").toString();
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'reportes.reporte-creado' AND aggregate_id = ?::uuid",
                Integer.class, reporteId));

        assertCodigo(409, "CONFLICT", enviar("POST", "/reportes", domiciliario.token(), Map.of(
                "motivo", "DOMICILIO", "descripcion", "Otra vez el mismo problema de entrega.", "domicilioId", domicilio)));
        assertCodigo(400, "VALIDATION_ERROR", enviar("POST", "/reportes", cliente.token(), Map.of(
                "motivo", "PEDIDO", "descripcion", "corta", "pedidoId", pedido)));
        assertCodigo(400, "VALIDATION_ERROR", enviar("POST", "/reportes", cliente.token(), Map.of(
                "motivo", "PEDIDO", "descripcion", "Pedido y domicilio a la vez.", "pedidoId", pedido,
                "domicilioId", domicilio)));
        Usuario extrano = registrar("CLIENTE");
        assertCodigo(404, "NOT_FOUND", enviar("POST", "/reportes", extrano.token(), Map.of(
                "motivo", "PEDIDO", "descripcion", "Un pedido que no es mío.", "pedidoId", pedido)));
        assertEquals(201, enviar("POST", "/reportes", cliente.token(), Map.of(
                "motivo", "COMPORTAMIENTO_USUARIO", "descripcion", "El domiciliario fue grosero.",
                "usuarioId", domiciliario.id())).statusCode());
        assertEquals(201, enviar("POST", "/reportes", extrano.token(), Map.of(
                "motivo", "PLATAFORMA", "descripcion", "La app se cierra al abrir el mapa.")).statusCode());

        String admin = adminToken();
        Map<String, Object> pendientes = body(enviar("GET", "/reportes?estado=PENDIENTE", admin, null));
        assertEquals(3, ((Number) pendientes.get("totalElements")).intValue());
        assertEquals(reporteId, contenido(pendientes).getFirst().get("id"));
        Map<String, Object> propios = body(enviar("GET", "/reportes", domiciliario.token(), null));
        assertEquals(1, ((Number) propios.get("totalElements")).intValue());
        assertCodigo(403, "FORBIDDEN", enviar("GET", "/reportes/" + reporteId, cliente.token(), null));

        Map<String, Object> detalle = body(enviar("GET", "/reportes/" + reporteId, admin, null));
        Map<?, ?> entidad = (Map<?, ?>) detalle.get("entidad");
        assertEquals(Boolean.TRUE, entidad.get("disponible"));
        assertEquals("ENTREGADO", ((Map<?, ?>) entidad.get("resumen")).get("estado"));

        HttpResponse<String> resuelto = enviar("POST", "/reportes/" + reporteId + "/resolver", admin,
                Map.of("nota", "Se contactó al cliente y se acordó una nueva entrega."));
        assertEquals(200, resuelto.statusCode(), resuelto.body());
        assertEquals("RESUELTO", body(resuelto).get("estado"));
        assertEquals("Admin Calificaciones", ((Map<?, ?>) body(resuelto).get("resueltoPor")).get("nombre"));
        assertCodigo(409, "INVALID_STATE", enviar("POST", "/reportes/" + reporteId + "/resolver", admin,
                Map.of("nota", "Segunda resolución")));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'reportes.reporte-resuelto' AND aggregate_id = ?::uuid",
                Integer.class, reporteId));
        assertFalse(body(enviar("GET", "/reportes?estado=PENDIENTE", admin, null)).get("content").toString()
                .contains(reporteId));
    }

    @Test
    void rf49_el_cliente_contacta_al_vendedor_y_al_domiciliario_en_camino() throws Exception {
        UUID enCamino = pedido("EN_CAMINO");
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO domicilio (id, pedido_id, vendedor_id, comprador_id, domiciliario_id, estado, tarifa,
                                       ubicacion_origen_id, ubicacion_destino_id, codigo_confirmacion, asignado_at,
                                       created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'EN_CAMINO', 5000, ?, ?, '654321', ?, ?, ?)
                """, UUID.randomUUID(), enCamino, vendedor.id(), cliente.id(), domiciliario.id(), ubicacion(),
                ubicacion(), ahora, ahora, ahora);
        String celularVendedor = jdbc.queryForObject("SELECT celular FROM usuario WHERE id = ?", String.class,
                vendedor.id());

        HttpResponse<String> llamada = enviar("POST", "/pedidos/" + enCamino + "/contactar", cliente.token(),
                Map.of("destinatario", "VENDEDOR", "tipo", "LLAMADA"));
        assertEquals(200, llamada.statusCode(), llamada.body());
        assertEquals("tel:" + celularVendedor, body(llamada).get("enlace"));
        HttpResponse<String> mensaje = enviar("POST", "/pedidos/" + enCamino + "/contactar", cliente.token(),
                Map.of("destinatario", "DOMICILIARIO", "tipo", "MENSAJE"));
        assertEquals(200, mensaje.statusCode(), mensaje.body());
        assertTrue(body(mensaje).get("enlace").toString().startsWith("https://wa.me/57"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM canal_contacto WHERE pedido_id = ?",
                Integer.class, enCamino));

        assertCodigo(409, "INVALID_STATE", enviar("POST", "/pedidos/" + pedido + "/contactar", cliente.token(),
                Map.of("destinatario", "VENDEDOR", "tipo", "LLAMADA")));
        assertCodigo(404, "NOT_FOUND", enviar("POST", "/pedidos/" + enCamino + "/contactar",
                registrar("CLIENTE").token(), Map.of("destinatario", "VENDEDOR", "tipo", "LLAMADA")));
        assertCodigo(400, "VALIDATION_ERROR", enviar("POST", "/pedidos/" + enCamino + "/contactar", cliente.token(),
                Map.of("destinatario", "OTRO", "tipo", "LLAMADA")));
        assertCodigo(403, "FORBIDDEN", enviar("POST", "/pedidos/" + enCamino + "/contactar", vendedor.token(),
                Map.of("destinatario", "VENDEDOR", "tipo", "LLAMADA")));

        // RF-52: el vendedor ve celular y dirección del pedido en curso; del entregado, solo el nombre.
        Map<String, Object> enCurso = body(enviar("GET", "/pedidos/" + enCamino + "/cliente", vendedor.token(), null));
        assertEquals(jdbc.queryForObject("SELECT celular FROM usuario WHERE id = ?", String.class, cliente.id()),
                enCurso.get("celular"));
        assertEquals("Calle 1", ((Map<?, ?>) enCurso.get("direccionEntrega")).get("direccion"));
        Map<String, Object> cerrado = body(enviar("GET", "/pedidos/" + pedido + "/cliente", vendedor.token(), null));
        assertNull(cerrado.get("celular"));
        assertCodigo(404, "NOT_FOUND", enviar("GET", "/pedidos/" + enCamino + "/cliente",
                registrar("VENDEDOR_AMBULANTE").token(), null));

        // RF-26: el domiciliario comparte su posición y el cliente la ve redondeada en la trazabilidad.
        UUID domicilioEnCamino = jdbc.queryForObject("SELECT id FROM domicilio WHERE pedido_id = ?", UUID.class, enCamino);
        HttpResponse<String> posicion = enviar("PUT", "/domicilios/" + domicilioEnCamino + "/ubicacion-domiciliario",
                domiciliario.token(), Map.of("latitud", 11.24537, "longitud", -74.20612));
        assertEquals(204, posicion.statusCode(), posicion.body());
        assertCodigo(429, "RATE_LIMITED", enviar("PUT", "/domicilios/" + domicilioEnCamino + "/ubicacion-domiciliario",
                domiciliario.token(), Map.of("latitud", 11.2, "longitud", -74.2)));
        Map<String, Object> traza = body(enviar("GET", "/domicilios/" + domicilioEnCamino + "/trazabilidad",
                cliente.token(), null));
        Map<?, ?> ubicacion = (Map<?, ?>) traza.get("ubicacionDomiciliario");
        assertEquals(11.245, ((Number) ubicacion.get("latitud")).doubleValue());
        assertCodigo(409, "INVALID_STATE", enviar("PUT", "/domicilios/" + domicilio + "/ubicacion-domiciliario",
                domiciliario.token(), Map.of("latitud", 11.2, "longitud", -74.2)));
    }

    private HttpResponse<String> calificar(Usuario autor, String tipo, UUID entidad, int puntaje, String comentario)
            throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of(
                "pedidoId", pedido, "tipoEntidad", tipo, "entidadId", entidad, "puntaje", puntaje));
        if (comentario != null) {
            cuerpo.put("comentario", comentario);
        }
        return enviar("POST", "/calificaciones", autor.token(), cuerpo);
    }

    private UUID pedido(String estado) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO pedido (id, usuario_id, emprendimiento_id, modalidad, estado, medio_pago, tarifa_domicilio,
                                    total, ubicacion_entrega_id, created_at, updated_at)
                VALUES (?, ?, ?, 'DOMICILIO', ?, 'CUENTA_BANCARIA', 5000, 8000, ?, ?, ?)
                """, id, cliente.id(), emprendimiento, estado, ubicacion(), ahora, ahora);
        jdbc.update("""
                INSERT INTO item_pedido (id, pedido_id, producto_id, cantidad, precio_unitario)
                VALUES (?, ?, ?, 1, 3000)
                """, UUID.randomUUID(), id, producto);
        return id;
    }

    private UUID ubicacion() {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO ubicacion (id, latitud, longitud, direccion, created_at, updated_at)
                VALUES (?, 10.4, -75.5, 'Calle 1', ?, ?)
                """, id, ahora, ahora);
        return id;
    }

    private Usuario registrar(String rol) throws Exception {
        String celular = "+573" + ThreadLocalRandom.current().nextLong(100_000_000, 1_000_000_000);
        HttpResponse<String> registro = enviar("POST", "/auth/register", null, Map.of(
                "nombre", "Usuario " + rol, "celular", celular, "password", "initial-password",
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
                "identificador", "calificaciones-admin@example.test", "password", "StrongPassword123"));
        assertEquals(200, login.statusCode(), login.body());
        return body(login).get("accessToken").toString();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + ruta))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", "calificaciones-test");
        if ("POST".equals(metodo) || "PATCH".equals(metodo)) {
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

    private Map<?, ?> detalles(HttpResponse<String> respuesta) throws Exception {
        return (Map<?, ?>) body(respuesta).get("details");
    }

    private static Map<?, ?> resumen(Map<String, Object> pagina) {
        return (Map<?, ?>) pagina.get("resumen");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> contenido(Map<String, Object> pagina) {
        return (List<Map<String, Object>>) pagina.get("content");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> contenidoDe(Map<String, Object> opciones) {
        return (List<Map<String, Object>>) opciones.get("opciones");
    }

    private record Usuario(UUID id, String token) {}
}
