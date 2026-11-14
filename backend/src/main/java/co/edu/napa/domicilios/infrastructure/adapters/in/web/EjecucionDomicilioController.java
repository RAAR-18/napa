package co.edu.napa.domicilios.infrastructure.adapters.in.web;

import co.edu.napa.domicilios.application.ports.in.ConsultarCodigoConfirmacionPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarCodigoConfirmacionPort.CodigoConfirmacion;
import co.edu.napa.domicilios.application.ports.in.ConsultarTrazabilidadPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarTrazabilidadPort.Trazabilidad;
import co.edu.napa.domicilios.application.ports.in.EjecutarDomicilioPort;
import co.edu.napa.domicilios.application.ports.in.EjecutarDomicilioPort.Transicion;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Recogida y salida (RF-23), entrega con código (RF-24), código del cliente (RF-25) y trazabilidad (RF-26). */
@RestController
public class EjecucionDomicilioController {
    private final EjecutarDomicilioPort ejecutar;
    private final ConsultarCodigoConfirmacionPort codigo;
    private final ConsultarTrazabilidadPort trazabilidad;

    public EjecucionDomicilioController(
            EjecutarDomicilioPort ejecutar,
            ConsultarCodigoConfirmacionPort codigo,
            ConsultarTrazabilidadPort trazabilidad
    ) {
        this.ejecutar = ejecutar;
        this.codigo = codigo;
        this.trazabilidad = trazabilidad;
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/confirmar-recogida")
    @PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
    public ResponseEntity<Transicion> confirmarRecogida(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ok(ejecutar.confirmarRecogida(usuario(jwt), domicilioId));
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/confirmar-salida")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<Transicion> confirmarSalida(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ok(ejecutar.confirmarSalida(usuario(jwt), domicilioId));
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/confirmar-entrega")
    @PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
    public ResponseEntity<Transicion> confirmarEntrega(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            @Valid @RequestBody ConfirmarEntregaRequest body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ok(ejecutar.confirmarEntrega(usuario(jwt), domicilioId, body.codigo()));
    }

    @GetMapping("/api/v1/domicilios/{domicilioId}/codigo-confirmacion")
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<CodigoConfirmacion> codigo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ok(codigo.consultarCodigo(usuario(jwt), domicilioId));
    }

    @GetMapping("/api/v1/domicilios/{domicilioId}/trazabilidad")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<Trazabilidad> trazabilidad(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ok(trazabilidad.consultarTrazabilidad(usuario(jwt), domicilioId));
    }

    public record ConfirmarEntregaRequest(
            @NotNull @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos.") String codigo
    ) {}

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static UUID usuario(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static void sinQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }
}
