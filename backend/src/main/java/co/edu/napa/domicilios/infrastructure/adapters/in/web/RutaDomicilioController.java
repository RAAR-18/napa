package co.edu.napa.domicilios.infrastructure.adapters.in.web;

import co.edu.napa.domicilios.application.ports.in.ConsultarRutaDomicilioPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarRutaDomicilioPort.Ruta;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** RF-22: ruta del domicilio asignado. La posición del domiciliario solo viaja en la consulta y no se guarda. */
@RestController
public class RutaDomicilioController {
    private static final Set<String> PARAMETROS = Set.of("lat", "lng");

    private final ConsultarRutaDomicilioPort rutas;

    public RutaDomicilioController(ConsultarRutaDomicilioPort rutas) {
        this.rutas = rutas;
    }

    @GetMapping("/api/v1/domicilios/{domicilioId}/ruta")
    @PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
    public ResponseEntity<Ruta> ruta(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            HttpServletRequest request
    ) {
        for (String name : request.getParameterMap().keySet()) {
            if (!PARAMETROS.contains(name)) {
                throw invalid(name, "Parámetro no admitido.");
            }
        }
        GeoPoint posicion = null;
        if (lat != null || lng != null) {
            if (lat == null) {
                throw invalid("lat", "Indica la latitud junto con la longitud.");
            }
            if (lng == null) {
                throw invalid("lng", "Indica la longitud junto con la latitud.");
            }
            if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
                throw invalid("lat", "La latitud debe estar entre -90 y 90.");
            }
            if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
                throw invalid("lng", "La longitud debe estar entre -180 y 180.");
            }
            posicion = new GeoPoint(lat, lng);
        }
        Ruta ruta = rutas.consultarRuta(UUID.fromString(jwt.getSubject()), domicilioId, posicion);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ruta);
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
