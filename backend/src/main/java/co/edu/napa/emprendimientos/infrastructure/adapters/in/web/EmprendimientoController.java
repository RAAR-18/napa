package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoPort;
import co.edu.napa.emprendimientos.application.usecase.CrearEmprendimientoCommand;
import co.edu.napa.emprendimientos.application.usecase.CrearEmprendimientoResult;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emprendimientos")
@Validated
public class EmprendimientoController {
    private final CrearEmprendimientoPort crearEmprendimiento;

    public EmprendimientoController(CrearEmprendimientoPort crearEmprendimiento) {
        this.crearEmprendimiento = crearEmprendimiento;
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
