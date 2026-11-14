package co.edu.napa.reportes.infrastructure.adapters.in.web;

import co.edu.napa.reportes.application.ports.in.ConsultarReportesPort;
import co.edu.napa.reportes.application.ports.in.CrearReportePort;
import co.edu.napa.reportes.application.ports.in.ResolverReportePort;
import co.edu.napa.reportes.domain.model.EstadoReporte;
import co.edu.napa.reportes.domain.model.MotivoReporte;
import co.edu.napa.reportes.domain.model.Reporte;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** RF-60 (crear), RF-61 (consultar) y RF-62 (resolver). */
@RestController
public class ReporteController {
    private static final Set<String> FILTROS = Set.of("estado", "page", "size", "sort");

    private final CrearReportePort crear;
    private final ConsultarReportesPort consultar;
    private final ResolverReportePort resolver;

    public ReporteController(CrearReportePort crear, ConsultarReportesPort consultar, ResolverReportePort resolver) {
        this.crear = Objects.requireNonNull(crear, "crear");
        this.consultar = Objects.requireNonNull(consultar, "consultar");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @PostMapping("/api/v1/reportes")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO', 'ROLE_DOMICILIARIO')")
    public ResponseEntity<ReporteCreado> crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CrearReporteRequest body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        Reporte reporte = crear.crear(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("rol"),
                new CrearReportePort.NuevoReporte(body.motivo(), body.descripcion(), body.pedidoId(),
                        body.domicilioId(), body.usuarioId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReporteCreado(reporte.id(), reporte.estado().name(),
                reporte.motivo().name(), reporte.descripcion(), reporte.createdAt()));
    }

    /** El administrador ve todos (RF-61); los demás, solo los suyos (RF-60 Edge 3). */
    @GetMapping("/api/v1/reportes")
    public ResponseEntity<ConsultarReportesPort.Pagina> listar(@AuthenticationPrincipal Jwt jwt,
                                                                HttpServletRequest request) {
        Map<String, String[]> parametros = request.getParameterMap();
        for (String nombre : parametros.keySet()) {
            if (!FILTROS.contains(nombre)) {
                throw invalido(nombre, "Filtro no permitido.");
            }
        }
        EstadoReporte estado = null;
        String valorEstado = valor(parametros, "estado");
        if (valorEstado != null) {
            try {
                estado = EstadoReporte.valueOf(valorEstado);
            } catch (IllegalArgumentException e) {
                throw invalido("estado", "Valor inválido.");
            }
        }
        int page = entero(parametros, "page", 0);
        int size = entero(parametros, "size", 20);
        if (page < 0) {
            throw invalido("page", "La página no puede ser negativa.");
        }
        if (size < 1 || size > 100) {
            throw invalido("size", "El tamaño debe estar entre 1 y 100.");
        }
        boolean ascendente = true;
        String sort = valor(parametros, "sort");
        if (sort != null) {
            String[] partes = sort.split(",", -1);
            if (partes.length > 2 || !"createdAt".equals(partes[0])
                    || (partes.length == 2 && !Set.of("asc", "desc").contains(partes[1].toLowerCase()))) {
                throw invalido("sort", "Ordena por createdAt,asc o createdAt,desc.");
            }
            ascendente = partes.length == 1 || "asc".equalsIgnoreCase(partes[1]);
        }
        boolean admin = "ADMINISTRADOR".equals(jwt.getClaimAsString("rol"));
        ConsultarReportesPort.Pagina pagina = admin
                ? consultar.listarTodos(estado, page, size, ascendente)
                : consultar.listarPropios(UUID.fromString(jwt.getSubject()), estado, page, size, ascendente);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pagina);
    }

    @GetMapping("/api/v1/reportes/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<ConsultarReportesPort.Detalle> detalle(@PathVariable UUID id, HttpServletRequest request) {
        sinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(consultar.detalle(id));
    }

    @PostMapping("/api/v1/reportes/{id}/resolver")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<ReporteResuelto> resolver(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) ResolverRequest body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        UUID administradorId = UUID.fromString(jwt.getSubject());
        Reporte resuelto = resolver.resolver(administradorId, id, body == null ? null : body.nota());
        ConsultarReportesPort.Detalle detalle = consultar.detalle(resuelto.id());
        return ResponseEntity.ok(new ReporteResuelto(resuelto.id(), resuelto.estado().name(),
                resuelto.notaResolucion(), detalle.resueltoPor(), resuelto.updatedAt()));
    }

    private static String valor(Map<String, String[]> parametros, String nombre) {
        String[] valores = parametros.get(nombre);
        if (valores == null) {
            return null;
        }
        if (valores.length != 1 || valores[0].isBlank()) {
            throw invalido(nombre, "Valor inválido.");
        }
        return valores[0].strip();
    }

    private static int entero(Map<String, String[]> parametros, String nombre, int defecto) {
        String valor = valor(parametros, nombre);
        if (valor == null) {
            return defecto;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw invalido(nombre, "Debe ser un número entero.");
        }
    }

    private static void sinQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }

    private static DomainException invalido(String campo, String mensaje) {
        return new DomainException("VALIDATION_ERROR", mensaje, Map.of("fields", Map.of(campo, mensaje)));
    }

    public record CrearReporteRequest(
            @NotNull MotivoReporte motivo,
            @NotNull String descripcion,
            UUID pedidoId,
            UUID domicilioId,
            UUID usuarioId
    ) {}

    public record ResolverRequest(String nota) {}

    public record ReporteCreado(UUID id, String estado, String motivo, String descripcion, Instant createdAt) {}

    public record ReporteResuelto(UUID id, String estado, String notaResolucion,
                                  ConsultarReportesPort.Persona resueltoPor, Instant updatedAt) {}
}
