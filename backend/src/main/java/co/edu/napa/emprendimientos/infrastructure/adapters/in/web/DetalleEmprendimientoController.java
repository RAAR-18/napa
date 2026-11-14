package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarDetalleEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.in.ConsultarDetalleEmprendimientoPort.Detalle;
import co.edu.napa.emprendimientos.application.ports.in.ConsultarDetalleEmprendimientoPort.Vendedor;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Detalle de un emprendimiento (RF-32) y datos públicos de su vendedor (RF-34), para el cliente. */
@RestController
public class DetalleEmprendimientoController {
    private final ConsultarDetalleEmprendimientoPort detalle;

    public DetalleEmprendimientoController(ConsultarDetalleEmprendimientoPort detalle) {
        this.detalle = detalle;
    }

    @GetMapping("/api/v1/emprendimientos/{emprendimientoId}")
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<Detalle> consultar(@PathVariable UUID emprendimientoId, HttpServletRequest request) {
        sinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detalle.consultar(emprendimientoId));
    }

    @GetMapping("/api/v1/emprendimientos/{emprendimientoId}/vendedor")
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<Vendedor> vendedor(@PathVariable UUID emprendimientoId, HttpServletRequest request) {
        sinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detalle.vendedor(emprendimientoId));
    }

    private static void sinQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }
}
