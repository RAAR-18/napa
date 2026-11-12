package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosPort;
import co.edu.napa.emprendimientos.application.ports.out.CampoOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.DireccionOrdenEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.out.FiltroEmprendimientos;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoCommand;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoResult;
import co.edu.napa.emprendimientos.application.ports.in.EmprendimientosPagina;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosQuery;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emprendimientos")
@Validated
public class EmprendimientoController {
    private static final Set<String> LIST_FILTERS = Set.of(
            "tipoVendedor", "lat", "lng", "radioMetros", "orden", "page", "size", "sort"
    );

    private final CrearEmprendimientoPort crearEmprendimiento;
    private final ListarEmprendimientosPort listarEmprendimientos;

    public EmprendimientoController(
            CrearEmprendimientoPort crearEmprendimiento,
            ListarEmprendimientosPort listarEmprendimientos
    ) {
        this.crearEmprendimiento = crearEmprendimiento;
        this.listarEmprendimientos = listarEmprendimientos;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<EmprendimientosPagina> listar(HttpServletRequest request) {
        EmprendimientosPagina page = listarEmprendimientos.listar(parseQuery(request.getParameterMap()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(page);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<EmprendimientoResponse> crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CrearEmprendimientoRequest request,
            HttpServletRequest servletRequest
    ) {
        if (!servletRequest.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Este recurso no admite parámetros de consulta.");
        }
        UUID usuarioId = parseUserId(jwt.getSubject());
        RolUsuario rol = parseRole(jwt.getClaimAsString("rol"));
        CrearEmprendimientoResult result = crearEmprendimiento.crear(new CrearEmprendimientoCommand(
                usuarioId,
                rol,
                request.nombre(),
                request.categoria(),
                request.descripcion(),
                request.horaApertura(),
                request.horaCierre(),
                request.ubicacion().latitud(),
                request.ubicacion().longitud(),
                request.ubicacion().direccion(),
                request.ubicacion().referencia()
        ));
        return ResponseEntity.created(URI.create("/api/v1/emprendimientos/" + result.emprendimiento().id()))
                .cacheControl(CacheControl.noStore())
                .body(EmprendimientoResponse.from(result));
    }

    private ListarEmprendimientosQuery parseQuery(Map<String, String[]> parameters) {
        for (String parameter : parameters.keySet()) {
            if (!LIST_FILTERS.contains(parameter)) {
                throw validationError(parameter, "El filtro no está permitido.");
            }
        }

        String type = value(parameters, "tipoVendedor");
        TipoVendedor tipoVendedor = null;
        if (type != null) {
            try {
                tipoVendedor = TipoVendedor.valueOf(type);
            } catch (IllegalArgumentException exception) {
                throw validationError("tipoVendedor", "El tipo de vendedor no es válido.");
            }
        }

        Double latitude = decimal(parameters, "lat");
        Double longitude = decimal(parameters, "lng");
        Integer radius = integer(parameters, "radioMetros");
        String order = value(parameters, "orden");
        boolean nearbyOrder = "cercania".equals(order);
        if (order != null && !nearbyOrder) {
            throw validationError("orden", "El orden solicitado no es válido.");
        }

        CampoOrdenEmprendimientos sortField = CampoOrdenEmprendimientos.CREATED_AT;
        DireccionOrdenEmprendimientos sortDirection = DireccionOrdenEmprendimientos.DESC;
        String sort = value(parameters, "sort");
        if (sort != null) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2) {
                throw validationError("sort", "Usa el formato campo,asc|desc.");
            }
            try {
                sortField = switch (parts[0]) {
                    case "nombre" -> CampoOrdenEmprendimientos.NOMBRE;
                    case "createdAt" -> CampoOrdenEmprendimientos.CREATED_AT;
                    default -> throw validationError("sort", "El campo de orden no está permitido.");
                };
                sortDirection = DireccionOrdenEmprendimientos.valueOf(parts[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw validationError("sort", "El orden solicitado no es válido.");
            }
        }

        int page = integerValue(parameters, "page", 0);
        int size = integerValue(parameters, "size", 20);
        return new ListarEmprendimientosQuery(
                new FiltroEmprendimientos(
                        tipoVendedor,
                        latitude,
                        longitude,
                        radius,
                        nearbyOrder,
                        sortField,
                        sortDirection
                ),
                page,
                size
        );
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

    private Double decimal(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            throw validationError(name, "El parámetro debe ser un número válido.");
        }
    }

    private Integer integer(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw validationError(name, "El parámetro debe ser un número entero.");
        }
    }

    private int integerValue(Map<String, String[]> parameters, String name, int defaultValue) {
        Integer value = integer(parameters, name);
        return value == null ? defaultValue : value;
    }

    private DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of(
                "fields",
                Map.of(field, message)
        ));
    }

    private UUID parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new DomainException("UNAUTHORIZED", "La sesión no contiene un identificador de usuario válido.");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("UNAUTHORIZED", "La sesión no contiene un identificador de usuario válido.");
        }
    }

    private RolUsuario parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new DomainException("FORBIDDEN", "No tienes permiso para realizar esta acción.");
        }
        try {
            return RolUsuario.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("FORBIDDEN", "No tienes permiso para realizar esta acción.");
        }
    }

    public record CrearEmprendimientoRequest(
            @NotBlank @Size(max = 100) String nombre,
            @NotNull CategoriaEmprendimiento categoria,
            @NotBlank @Size(max = 500) String descripcion,
            @NotNull LocalTime horaApertura,
            @NotNull LocalTime horaCierre,
            @NotNull @Valid UbicacionRequest ubicacion
    ) {
    }

    public record UbicacionRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitud,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitud,
            @Size(max = 300) String direccion,
            @Size(max = 300) String referencia
    ) {
    }

    public record EmprendimientoResponse(
            UUID id,
            String nombre,
            CategoriaEmprendimiento categoria,
            String descripcion,
            RolUsuario tipoVendedor,
            LocalTime horaApertura,
            LocalTime horaCierre,
            String estado,
            UbicacionResponse ubicacion
    ) {
        private static EmprendimientoResponse from(CrearEmprendimientoResult result) {
            Emprendimiento emprendimiento = result.emprendimiento();
            Ubicacion ubicacion = result.ubicacion();
            return new EmprendimientoResponse(
                    emprendimiento.id(),
                    emprendimiento.nombre(),
                    emprendimiento.categoria(),
                    emprendimiento.descripcion(),
                    result.tipoVendedor(),
                    emprendimiento.horaApertura(),
                    emprendimiento.horaCierre(),
                    emprendimiento.estado().name(),
                    new UbicacionResponse(
                            ubicacion.latitud(),
                            ubicacion.longitud(),
                            ubicacion.direccion(),
                            ubicacion.referencia()
                    )
            );
        }
    }

    public record UbicacionResponse(double latitud, double longitud, String direccion, String referencia) {
    }
}
