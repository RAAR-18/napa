package co.edu.napa.domicilios.infrastructure.adapters.in.web;

import co.edu.napa.domicilios.application.ports.in.AceptarDomicilioPort;
import co.edu.napa.domicilios.application.ports.in.AceptarDomicilioPort.DomicilioAceptado;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDomiciliarioPort;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosDisponiblesPort;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosDisponiblesPort.Orden;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosEnCursoPort;
import co.edu.napa.domicilios.application.ports.in.RechazarDomicilioPort;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Endpoints del domiciliario: RF-16, RF-17, RF-18 y RF-19. */
@RestController
@PreAuthorize("hasAuthority('ROLE_DOMICILIARIO')")
public class DomiciliarioController {
    private static final Set<String> FILTROS_DISPONIBLES = Set.of("orden", "lat", "lng", "page", "size");

    private final ListarDomiciliosDisponiblesPort disponibles;
    private final ConsultarDomicilioDomiciliarioPort detalle;
    private final RechazarDomicilioPort rechazar;
    private final AceptarDomicilioPort aceptar;
    private final ListarDomiciliosEnCursoPort enCurso;
    private final SupervisarDomiciliosPort supervisar;

    public DomiciliarioController(
            ListarDomiciliosDisponiblesPort disponibles,
            ConsultarDomicilioDomiciliarioPort detalle,
            RechazarDomicilioPort rechazar,
            AceptarDomicilioPort aceptar,
            ListarDomiciliosEnCursoPort enCurso,
            SupervisarDomiciliosPort supervisar
    ) {
        this.disponibles = disponibles;
        this.detalle = detalle;
        this.rechazar = rechazar;
        this.aceptar = aceptar;
        this.enCurso = enCurso;
        this.supervisar = supervisar;
    }

    @GetMapping("/api/v1/domicilios/disponibles")
    public ResponseEntity<ListarDomiciliosDisponiblesPort.Pagina> disponibles(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        Map<String, String[]> parameters = request.getParameterMap();
        for (String parameter : parameters.keySet()) {
            if (!FILTROS_DISPONIBLES.contains(parameter)) {
                throw validationError(parameter, "El filtro no está permitido.");
            }
        }
        Orden orden = switch (value(parameters, "orden") == null ? "" : value(parameters, "orden")) {
            case "" -> Orden.RECIENTES;
            case "cercania" -> Orden.CERCANIA;
            case "ganancia" -> Orden.GANANCIA;
            default -> throw validationError("orden", "Usa cercania o ganancia.");
        };
        Double lat = doubleValue(parameters, "lat");
        Double lng = doubleValue(parameters, "lng");
        if ((lat == null) != (lng == null)) {
            throw validationError(lat == null ? "lat" : "lng", "Envía la latitud y la longitud juntas.");
        }
        GeoPoint posicion = lat == null ? null : new GeoPoint(lat, lng);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(disponibles.listar(
                usuario(jwt), orden, posicion, intValue(parameters, "page", 0), intValue(parameters, "size", 20)));
    }

    @GetMapping("/api/v1/domicilios/en-curso")
    public ResponseEntity<List<ListarDomiciliosEnCursoPort.Item>> enCurso(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enCurso.listar(usuario(jwt)));
    }

    /** RF-18 para el domiciliario; RF-12 (detalle completo, sin el código) para el administrador. */
    @GetMapping("/api/v1/domicilios/{domicilioId}")
    @PreAuthorize("hasAnyAuthority('ROLE_DOMICILIARIO', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<?> detalle(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMINISTRADOR".equals(authority.getAuthority()));
        Object body = admin ? supervisar.consultar(domicilioId) : detalle.consultar(usuario(jwt), domicilioId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/rechazar")
    public ResponseEntity<Void> rechazar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        rechazar.rechazar(usuario(jwt), domicilioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/domicilios/{domicilioId}/aceptar")
    public ResponseEntity<DomicilioAceptado> aceptar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID domicilioId,
            HttpServletRequest request
    ) {
        sinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(aceptar.aceptar(usuario(jwt), domicilioId));
    }

    private static UUID usuario(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static void sinQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }

    private static String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        if (values == null) {
            return null;
        }
        if (values.length != 1 || values[0] == null || values[0].isBlank()) {
            throw validationError(name, "El parámetro debe tener un único valor no vacío.");
        }
        return values[0];
    }

    private static Double doubleValue(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            double limite = name.equals("lat") ? 90 : 180;
            if (!Double.isFinite(parsed) || Math.abs(parsed) > limite) {
                throw validationError(name, "La coordenada está fuera de rango.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw validationError(name, "La coordenada debe ser un número.");
        }
    }

    private static int intValue(Map<String, String[]> parameters, String name, int defaultValue) {
        String value = value(parameters, name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw validationError(name, "El parámetro debe ser un número entero.");
        }
    }

    private static DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
