package co.edu.napa.pagos.infrastructure.adapters.in.web;

import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.Filtros;
import co.edu.napa.pagos.application.ports.in.ConsultarHistorialPagosPort.RolHistorial;
import co.edu.napa.pagos.domain.model.EstadoPago;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.TipoPago;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** {@code GET /pagos}: el administrador ve todos (RF-41); los demás roles, solo los suyos (RF-40). */
@RestController
public class HistorialPagosController {
    private static final Set<String> PARAMETROS = Set.of(
            "medio", "estado", "tipo", "desde", "hasta", "page", "size", "sort");

    private final ConsultarHistorialPagosPort historial;

    public HistorialPagosController(ConsultarHistorialPagosPort historial) {
        this.historial = historial;
    }

    @GetMapping("/api/v1/pagos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Map<String, String[]> parameters = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            if (!PARAMETROS.contains(entry.getKey())) throw invalid(entry.getKey(), "Parámetro no admitido.");
            if (entry.getValue().length != 1) throw invalid(entry.getKey(), "Indica el parámetro una sola vez.");
        }
        Filtros filtros = filtros(parameters);
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        Object body;
        if (roles.contains("ROLE_ADMINISTRADOR")) {
            body = historial.consultarGeneral(filtros);
        } else {
            RolHistorial rol;
            if (roles.contains("ROLE_CLIENTE")) {
                rol = RolHistorial.CLIENTE;
            } else if (roles.contains("ROLE_VENDEDOR_AMBULANTE") || roles.contains("ROLE_VENDEDOR_PUNTO_FIJO")) {
                rol = RolHistorial.VENDEDOR;
            } else if (roles.contains("ROLE_DOMICILIARIO")) {
                rol = RolHistorial.DOMICILIARIO;
            } else {
                throw new DomainException("FORBIDDEN", "No tienes acceso al historial de pagos.");
            }
            body = historial.consultarPropio(UUID.fromString(jwt.getSubject()), rol, filtros);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static Filtros filtros(Map<String, String[]> parameters) {
        boolean porMonto = false;
        boolean ascendente = false;
        String sort = value(parameters, "sort");
        if (sort != null) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !Set.of("createdAt", "monto").contains(parts[0])
                    || !Set.of("asc", "desc").contains(parts[1])) {
                throw invalid("sort", "Usa createdAt o monto, seguido de asc o desc.");
            }
            porMonto = parts[0].equals("monto");
            ascendente = parts[1].equals("asc");
        }
        return new Filtros(
                enumValue(parameters, "medio", MedioPago.class),
                enumValue(parameters, "estado", EstadoPago.class),
                enumValue(parameters, "tipo", TipoPago.class),
                date(parameters, "desde"),
                date(parameters, "hasta"),
                porMonto,
                ascendente,
                integer(parameters, "page", 0),
                integer(parameters, "size", 20));
    }

    private static String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values[0];
    }

    private static <E extends Enum<E>> E enumValue(Map<String, String[]> parameters, String name, Class<E> type) {
        String value = value(parameters, name);
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw invalid(name, "El valor de " + name + " no es válido.");
        }
    }

    private static LocalDate date(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(name, "Usa una fecha con el formato AAAA-MM-DD.");
        }
    }

    private static int integer(Map<String, String[]> parameters, String name, int defaultValue) {
        String value = value(parameters, name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(name, "Debe ser un número entero.");
        }
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
