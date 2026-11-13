package co.edu.napa.productos.infrastructure.adapters.in.web;

import co.edu.napa.productos.application.ports.in.ProductosPort;
import co.edu.napa.productos.domain.model.EstadoProducto;
import co.edu.napa.productos.domain.model.Producto;
import co.edu.napa.productos.domain.model.UnidadProducto;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class ProductoController {
    private final ProductosPort productos;
    private final ObjectMapper json;

    public ProductoController(ProductosPort productos, ObjectMapper json) {
        this.productos = productos;
        this.json = json;
    }

    @PostMapping("/api/v1/productos")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<ProductoResponse> crear(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CrearRequest body, HttpServletRequest request) {
        noQuery(request);
        Producto value = productos.crear(UUID.fromString(jwt.getSubject()), new ProductosPort.Datos(
                body.nombre(), body.precio(), body.unidad(), body.descripcion(), body.cantidadDisponible()));
        return ResponseEntity.created(URI.create("/api/v1/productos/" + value.id()))
                .cacheControl(CacheControl.noStore()).body(ProductoResponse.from(value));
    }

    @GetMapping("/api/v1/productos")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PaginaResponse> propios(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return page(productos.propios(UUID.fromString(jwt.getSubject()), query(request, true)));
    }

    @GetMapping("/api/v1/emprendimientos/{id}/productos")
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<PaginaResponse> catalogo(@PathVariable UUID id, HttpServletRequest request) {
        return page(productos.catalogo(id, query(request, false)));
    }

    @PostMapping("/api/v1/productos/{id}/inventario")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<ProductoResponse> inventario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody InventarioRequest body, HttpServletRequest request) {
        noQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ProductoResponse.from(
                productos.actualizarInventario(UUID.fromString(jwt.getSubject()), id, body.cantidadDisponible())));
    }

    @GetMapping("/api/v1/productos/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<ProductoResponse> propio(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                   HttpServletRequest request) {
        noQuery(request);
        return product(productos.consultarPropio(UUID.fromString(jwt.getSubject()), id));
    }

    @PatchMapping("/api/v1/productos/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<ProductoResponse> editar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody String rawBody, @RequestHeader(value = "If-Match", required = false) String version,
            HttpServletRequest request) {
        noQuery(request);
        Map<String, Object> body;
        try {
            // Evita que un decimal de precio se redondee a double antes de comprobar que sea entero.
            body = json.readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(rawBody);
        } catch (tools.jackson.core.JacksonException exception) {
            throw invalid("body", "Escribe un objeto JSON con los campos que quieres editar.");
        }
        if (body == null || body.isEmpty()) throw invalid("body", "Indica al menos un campo para editar.");
        for (String field : body.keySet()) {
            if (!Set.of("nombre", "precio", "descripcion").contains(field)) {
                throw invalid(field, "Este campo no se puede editar aquí.");
            }
        }
        String nombre = null;
        if (body.containsKey("nombre")) {
            if (!(body.get("nombre") instanceof String text)) throw invalid("nombre", "El nombre es obligatorio.");
            nombre = text;
        }
        BigDecimal precio = null;
        if (body.containsKey("precio")) {
            if (!(body.get("precio") instanceof Number number)) throw invalid("precio", "El precio debe ser un entero positivo en pesos.");
            precio = new BigDecimal(number.toString());
        }
        Object description = body.get("descripcion");
        if (description != null && !(description instanceof String)) throw invalid("descripcion", "Escribe una descripción válida.");
        return product(productos.editar(UUID.fromString(jwt.getSubject()), id, new ProductosPort.Edicion(
                nombre, precio, body.containsKey("descripcion"), (String) description, expectedVersion(version))));
    }

    @PostMapping("/api/v1/productos/{id}/eliminar")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        noQuery(request);
        if (body == null || body.size() != 1 || !Boolean.TRUE.equals(body.get("confirmar"))) {
            throw invalid("confirmar", "Confirma la eliminación del producto.");
        }
        productos.eliminar(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private Long expectedVersion(String header) {
        if (header == null) return null;
        if (!header.matches("\"[0-9]+\"")) throw invalid("If-Match", "La versión del producto no es válida.");
        try {
            return Long.parseLong(header.substring(1, header.length() - 1));
        } catch (NumberFormatException exception) {
            throw invalid("If-Match", "La versión del producto no es válida.");
        }
    }

    private ResponseEntity<ProductoResponse> product(Producto value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(Long.toString(value.version()))
                .body(ProductoResponse.from(value));
    }

    private DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }

    private ResponseEntity<PaginaResponse> page(ProductosPort.Pagina page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PaginaResponse(
                page.content().stream().map(ProductoResponse::from).toList(), page.page(), page.size(),
                page.totalElements(), page.emprendimiento()));
    }

    private ProductosPort.Consulta query(HttpServletRequest request, boolean seller) {
        Set<String> allowed = seller ? Set.of("page", "size", "sort", "estado") : Set.of("page", "size", "sort");
        request.getParameterMap().forEach((key, values) -> {
            if (!allowed.contains(key) || values.length != 1 || values[0].isBlank()) {
                throw new DomainException("VALIDATION_ERROR", "El filtro " + key + " no es válido.");
            }
        });
        try {
            return new ProductosPort.Consulta(
                    request.getParameter("page") == null ? 0 : Integer.parseInt(request.getParameter("page")),
                    request.getParameter("size") == null ? 20 : Integer.parseInt(request.getParameter("size")),
                    request.getParameter("sort") == null ? "nombre,asc" : request.getParameter("sort"),
                    request.getParameter("estado") == null ? null : EstadoProducto.valueOf(request.getParameter("estado")));
        } catch (IllegalArgumentException exception) {
            throw new DomainException("VALIDATION_ERROR", "Revisa los filtros del listado.");
        }
    }

    private void noQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Este recurso no admite parámetros de consulta.");
        }
    }

    public record CrearRequest(@NotBlank @Size(max = 100) String nombre,
            @NotNull @DecimalMin("1") @DecimalMax("9007199254740991") @Digits(integer = 16, fraction = 0) BigDecimal precio,
            @NotNull UnidadProducto unidad, @Size(max = 500) String descripcion,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal cantidadDisponible) {}

    public record InventarioRequest(@NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal cantidadDisponible) {}

    public record ProductoResponse(UUID id, UUID emprendimientoId, String nombre, long precio, UnidadProducto unidad,
            String descripcion, BigDecimal cantidadDisponible, EstadoProducto estado, boolean soloReserva, Instant updatedAt,
            long version) {
        static ProductoResponse from(Producto value) {
            return new ProductoResponse(value.id(), value.emprendimientoId(), value.nombre(), value.precio(),
                    value.unidad(), value.descripcion(), value.cantidadDisponible(), value.estado(),
                    value.estado() == EstadoProducto.AGOTADO, value.updatedAt(), value.version());
        }
    }

    public record PaginaResponse(List<ProductoResponse> content, int page, int size, long totalElements,
                                 ProductosPort.ResumenEmprendimiento emprendimiento) {}
}
