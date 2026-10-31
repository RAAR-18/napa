package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.emprendimientos.application.ports.in.CrearProductoPort;
import co.edu.napa.emprendimientos.application.ports.in.ListarProductosPort;
import co.edu.napa.emprendimientos.domain.model.Producto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {
    private final CrearProductoPort crearProducto;
    private final ListarProductosPort listarProductos;

    public ProductoController(CrearProductoPort crearProducto, ListarProductosPort listarProductos) {
        this.crearProducto = crearProducto;
        this.listarProductos = listarProductos;
    }

    @GetMapping
    public ListarProductosPort.Resultado listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return listarProductos.listar(page, size);
    }

    @GetMapping("/mios")
    public ListarProductosPort.Resultado listarMios(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return listarProductos.listarDelVendedor(UUID.fromString(jwt.getSubject()), page, size);
    }

    @PostMapping
    public ResponseEntity<ListarProductosPort.Item> crear(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CrearProductoRequest request
    ) {
        Producto producto = crearProducto.crear(
                UUID.fromString(jwt.getSubject()), request.nombre(), request.precio(),
                request.unidad(), request.descripcion(), request.cantidadDisponible(), request.imagenUrl()
        );
        return ResponseEntity.created(URI.create("/api/v1/productos/" + producto.id()))
                .body(new ListarProductosPort.Item(
                        producto.id(), producto.emprendimientoId(), null, producto.nombre(),
                        producto.precio(), producto.unidad(), producto.descripcion(),
                        producto.cantidadDisponible(), producto.imagenUrl()
                ));
    }

    public record CrearProductoRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal precio,
            @NotBlank @Size(max = 40) String unidad,
            @Size(max = 500) String descripcion,
            @Min(1) int cantidadDisponible,
            @Size(max = 2_000_000) String imagenUrl
    ) {}
}
