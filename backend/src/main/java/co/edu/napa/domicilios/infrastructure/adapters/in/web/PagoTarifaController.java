package co.edu.napa.domicilios.infrastructure.adapters.in.web;

import co.edu.napa.domicilios.application.ports.in.PagoTarifaPort;
import co.edu.napa.domicilios.application.ports.in.PagoTarifaPort.EstadoPagoTarifa;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** RF-43: pago de la tarifa del domicilio. Los dos POST pasan por el filtro de idempotencia. */
@RestController
public class PagoTarifaController {
    private final PagoTarifaPort pagos;

    public PagoTarifaController(PagoTarifaPort pagos) {
        this.pagos = pagos;
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/pago-tarifa")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<EstadoPagoTarifa> registrar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        if (body != null && !body.isEmpty()) {
            String message = "El pago de la tarifa es solo por cuenta bancaria y por el valor publicado.";
            throw new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of("body", message)));
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(pagos.registrar(UUID.fromString(jwt.getSubject()), domicilioId));
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/pago-tarifa/confirmar")
    @PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
    public ResponseEntity<EstadoPagoTarifa> confirmar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pagos.confirmar(UUID.fromString(jwt.getSubject()), domicilioId));
    }

    @GetMapping("/api/v1/domicilios/{domicilioId}/pago-tarifa")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_PUNTO_FIJO', 'ROLE_DOMICILIARIO')")
    public ResponseEntity<EstadoPagoTarifa> consultar(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        boolean vendedor = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_VENDEDOR_PUNTO_FIJO".equals(authority.getAuthority()));
        EstadoPagoTarifa estado = vendedor
                ? pagos.consultarComoVendedor(usuarioId, domicilioId)
                : pagos.consultarComoDomiciliario(usuarioId, domicilioId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado);
    }

    private static void sinQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }
}
