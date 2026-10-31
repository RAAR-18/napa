package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.emprendimientos.application.ports.in.CrearEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.in.ListarEmprendimientosPort;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emprendimientos")
public class EmprendimientoController {
    private final CrearEmprendimientoPort crearEmprendimiento;
    private final ListarEmprendimientosPort listarEmprendimientos;
    private final UbicacionRepositoryPort ubicaciones;

    public EmprendimientoController(
            CrearEmprendimientoPort crearEmprendimiento,
            UbicacionRepositoryPort ubicaciones,
            ListarEmprendimientosPort listarEmprendimientos
    ) {
        this.crearEmprendimiento = crearEmprendimiento;
        this.ubicaciones = ubicaciones;
        this.listarEmprendimientos = listarEmprendimientos;
    }

    @GetMapping
    public ListarEmprendimientosPort.Resultado listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return listarEmprendimientos.listar(page, size);
    }

    @PostMapping
    public ResponseEntity<EmprendimientoResponse> crear(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CrearEmprendimientoRequest request
    ) {
        Emprendimiento emprendimiento = crearEmprendimiento.crear(
                UUID.fromString(jwt.getSubject()),
                request.nombre(),
                request.categoria(),
                request.descripcion(),
                request.imagenUrl(),
                request.horaApertura(),
                request.horaCierre(),
                new CrearEmprendimientoPort.UbicacionInput(
                        request.ubicacion().latitud(),
                        request.ubicacion().longitud(),
                        request.ubicacion().direccion(),
                        request.ubicacion().referencia()
                )
        );
        return ResponseEntity.created(URI.create("/api/v1/emprendimientos/" + emprendimiento.id()))
                .body(EmprendimientoResponse.desde(
                        emprendimiento,
                        ubicaciones.findById(emprendimiento.ubicacionId())
                                .orElseThrow(() -> new IllegalStateException("La ubicación creada no está disponible."))
                ));
    }

    public record CrearEmprendimientoRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotNull CategoriaEmprendimiento categoria,
            @Size(max = 500) String descripcion,
            @Size(max = 2_000_000) String imagenUrl,
            @NotNull LocalTime horaApertura,
            @NotNull LocalTime horaCierre,
            @NotNull @Valid UbicacionRequest ubicacion
    ) {
    }

    public record UbicacionRequest(
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitud,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitud,
            @NotBlank @Size(max = 300) String direccion,
            @Size(max = 300) String referencia
    ) {
    }

    public record EmprendimientoResponse(
            UUID id,
            String nombre,
            CategoriaEmprendimiento categoria,
            String descripcion,
            String imagenUrl,
            String tipoVendedor,
            LocalTime horaApertura,
            LocalTime horaCierre,
            String estado,
            UbicacionResponse ubicacion
    ) {
        static EmprendimientoResponse desde(
                Emprendimiento emprendimiento,
                co.edu.napa.cuentas.domain.model.Ubicacion ubicacion
        ) {
            return new EmprendimientoResponse(
                    emprendimiento.id(),
                    emprendimiento.nombre(),
                    emprendimiento.categoria(),
                    emprendimiento.descripcion(),
                    emprendimiento.imagenUrl(),
                    emprendimiento.tipoVendedor().name(),
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

    public record UbicacionResponse(
            double latitud,
            double longitud,
            String direccion,
            String referencia
    ) {
    }
}
