package co.edu.napa.domicilios.infrastructure.adapters.in.web;

import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosVendedorPort;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosVendedorPort.Filtro;
import co.edu.napa.domicilios.application.ports.in.ListarDomiciliosVendedorPort.Pagina;
import co.edu.napa.domicilios.application.ports.in.PublicarDomicilioPort;
import co.edu.napa.domicilios.application.ports.in.PublicarDomicilioPort.DomicilioPublicado;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.shared.domain.DomainException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class DomicilioController {
    private static final Set<String> LIST_FILTERS = Set.of("estado", "desde", "hasta", "page", "size", "sort");
    private static final Set<String> ADMIN_FILTERS =
            Set.of("estado", "desde", "hasta", "vendedorId", "domiciliarioId", "page", "size", "sort");
    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");

    private final PublicarDomicilioPort publicarDomicilio;
    private final ListarDomiciliosVendedorPort listarDomiciliosVendedor;
    private final SupervisarDomiciliosPort supervisarDomicilios;

    public DomicilioController(
            PublicarDomicilioPort publicarDomicilio,
            ListarDomiciliosVendedorPort listarDomiciliosVendedor,
            SupervisarDomiciliosPort supervisarDomicilios
    ) {
        this.publicarDomicilio = publicarDomicilio;
        this.listarDomiciliosVendedor = listarDomiciliosVendedor;
        this.supervisarDomicilios = supervisarDomicilios;
    }

    /** RF-14: publicar el domicilio de un pedido aceptado y pagado. */
    @PostMapping("/api/v1/pedidos/{pedidoId}/domicilio")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<DomicilioPublicado> publicar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        DomicilioPublicado publicado = publicarDomicilio.publicar(pedidoId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/domicilios/" + publicado.id()))
                .cacheControl(CacheControl.noStore()).body(publicado);
    }

    /** RF-15: domicilios del vendedor de punto fijo. RF-12: todos los domicilios, para el administrador. */
    @GetMapping("/api/v1/domicilios")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_PUNTO_FIJO', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Map<String, String[]> parameters = request.getParameterMap();
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMINISTRADOR".equals(authority.getAuthority()));
        Set<String> permitidos = admin ? ADMIN_FILTERS : LIST_FILTERS;
        for (String parameter : parameters.keySet()) {
            if (!permitidos.contains(parameter)) {
                throw validationError(parameter, "El filtro no está permitido.");
            }
        }
        if (admin) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                    supervisarDomicilios.listar(
                            filtroAdmin(parameters),
                            integerValue(parameters, "page", 0),
                            integerValue(parameters, "size", 20)));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                listarDomiciliosVendedor.listar(
                        UUID.fromString(jwt.getSubject()),
                        filtro(parameters),
                        integerValue(parameters, "page", 0),
                        integerValue(parameters, "size", 20)));
    }

    /** RF-14 FR-006: un domicilio publicado no se edita ni se borra. */
    @RequestMapping(value = "/api/v1/domicilios/{domicilioId}",
            method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<Void> noEditable(@PathVariable String domicilioId) {
        throw new DomainException("METHOD_NOT_ALLOWED", "Un domicilio publicado no se puede modificar.");
    }

    private SupervisarDomiciliosPort.Filtro filtroAdmin(Map<String, String[]> parameters) {
        boolean porActualizacion = false;
        boolean ascendente = false;
        String sort = value(parameters, "sort");
        if (sort != null) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !Set.of("createdAt", "updatedAt").contains(parts[0])) {
                throw validationError("sort", "Usa createdAt o updatedAt, con asc o desc.");
            }
            porActualizacion = parts[0].equals("updatedAt");
            ascendente = switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "asc" -> true;
                case "desc" -> false;
                default -> throw validationError("sort", "El orden solicitado no es válido.");
            };
        }
        Map<String, String[]> sinOrden = new HashMap<>(parameters);
        sinOrden.remove("sort");
        Filtro base = filtro(sinOrden);
        return new SupervisarDomiciliosPort.Filtro(base.estado(), base.desde(), base.hasta(),
                uuidValue(parameters, "vendedorId"), uuidValue(parameters, "domiciliarioId"), porActualizacion,
                ascendente);
    }

    private UUID uuidValue(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw validationError(name, "El identificador no es válido.");
        }
    }

    private Filtro filtro(Map<String, String[]> parameters) {
        EstadoDomicilio estado = null;
        String estadoValue = value(parameters, "estado");
        if (estadoValue != null) {
            try {
                estado = EstadoDomicilio.valueOf(estadoValue);
            } catch (IllegalArgumentException exception) {
                throw validationError("estado", "El valor del filtro no es válido.");
            }
        }
        LocalDate desde = dateValue(parameters, "desde");
        LocalDate hasta = dateValue(parameters, "hasta");
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw validationError("desde", "La fecha inicial no puede ser posterior a la fecha final.");
        }
        boolean ascendente = false;
        String sort = value(parameters, "sort");
        if (sort != null) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !parts[0].equals("createdAt")) {
                throw validationError("sort", "Usa createdAt,asc o createdAt,desc.");
            }
            ascendente = switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "asc" -> true;
                case "desc" -> false;
                default -> throw validationError("sort", "El orden solicitado no es válido.");
            };
        }
        Instant from;
        Instant until;
        try {
            from = desde == null ? null : desde.atStartOfDay(COLOMBIA_ZONE).toInstant();
            until = hasta == null ? null : hasta.plusDays(1).atStartOfDay(COLOMBIA_ZONE).toInstant();
        } catch (DateTimeException exception) {
            throw validationError("hasta", "La fecha está fuera del rango permitido.");
        }
        return new Filtro(estado, from, until, ascendente);
    }

    private String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        if (values == null) {
            return null;
        }
        if (values.length != 1 || values[0] == null || values[0].isBlank()) {
            throw validationError(name, "El parámetro debe tener un único valor no vacío.");
        }
        return values[0];
    }

    private LocalDate dateValue(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw validationError(name, "La fecha debe usar el formato ISO-8601 (yyyy-MM-dd).");
        }
    }

    private int integerValue(Map<String, String[]> parameters, String name, int defaultValue) {
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

    private DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
