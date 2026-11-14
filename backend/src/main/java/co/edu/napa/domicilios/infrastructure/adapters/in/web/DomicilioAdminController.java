package co.edu.napa.domicilios.infrastructure.adapters.in.web;

import co.edu.napa.domicilios.application.ports.in.CancelarDomicilioAdminPort;
import co.edu.napa.domicilios.application.ports.in.CancelarDomicilioAdminPort.Cancelacion;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** RF-13: el administrador cancela un domicilio con un motivo. La {@code Idempotency-Key} la exige el filtro. */
@RestController
public class DomicilioAdminController {
    private final CancelarDomicilioAdminPort cancelar;

    public DomicilioAdminController(CancelarDomicilioAdminPort cancelar) {
        this.cancelar = cancelar;
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/cancelar")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<Cancelacion> cancelar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        if (body != null && !Set.of("motivo").containsAll(body.keySet())) {
            throw new DomainException("VALIDATION_ERROR", "Solo se admite el campo motivo.");
        }
        Object motivo = body == null ? null : body.get("motivo");
        if (motivo != null && !(motivo instanceof String)) {
            String message = "El motivo debe ser texto.";
            throw new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of("motivo", message)));
        }
        Cancelacion cancelacion = cancelar.cancelar(UUID.fromString(jwt.getSubject()), domicilioId, (String) motivo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cancelacion);
    }
}
