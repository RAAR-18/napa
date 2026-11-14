package co.edu.napa.domicilios.infrastructure.adapters.in.web;

import co.edu.napa.domicilios.application.ports.in.ActualizarUbicacionDomiciliarioPort;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/** RF-26: posición del domiciliario en camino. Sin Idempotency-Key: reemplaza un dato efímero. */
@RestController
public class UbicacionDomiciliarioController {
    private final ActualizarUbicacionDomiciliarioPort ubicaciones;

    public UbicacionDomiciliarioController(ActualizarUbicacionDomiciliarioPort ubicaciones) {
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
    }

    @PutMapping("/api/v1/domicilios/{domicilioId}/ubicacion-domiciliario")
    @PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
    public ResponseEntity<Void> actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            @Valid @RequestBody PosicionRequest body,
            HttpServletRequest request
    ) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        ubicaciones.actualizarUbicacion(UUID.fromString(jwt.getSubject()), domicilioId, body.latitud(), body.longitud());
        return ResponseEntity.noContent().build();
    }

    public record PosicionRequest(@NotNull Double latitud, @NotNull Double longitud) {}
}
