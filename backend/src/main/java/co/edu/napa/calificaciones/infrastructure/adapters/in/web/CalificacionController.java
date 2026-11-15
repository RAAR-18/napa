package co.edu.napa.calificaciones.infrastructure.adapters.in.web;

import co.edu.napa.calificaciones.application.ports.in.CalificacionPropiaPort;
import co.edu.napa.calificaciones.application.ports.in.CalificarPort;
import co.edu.napa.calificaciones.application.ports.in.ConsultarCalificacionesPort;
import co.edu.napa.calificaciones.application.ports.in.ConsultarCalificacionesPort.Consulta;
import co.edu.napa.calificaciones.application.ports.in.ModerarCalificacionPort;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.RolUsuario;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** RF-01 a RF-06: calificar, consultar, editar, eliminar y moderar calificaciones. */
@RestController
public class CalificacionController {
    private static final String PARTICIPANTES =
            "hasAnyAuthority('ROLE_CLIENTE', 'ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO', 'ROLE_DOMICILIARIO')";
    private static final Set<String> FILTROS = Set.of("tipoEntidad", "entidadId", "page", "size", "sort");
    private static final Set<String> FILTROS_ADMIN = Set.of("tipoEntidad", "entidadId", "estado", "page", "size", "sort");
    private static final Set<String> CAMPOS_EDICION = Set.of("puntaje", "comentario");

    private final CalificarPort calificar;
    private final ConsultarCalificacionesPort consultar;
    private final CalificacionPropiaPort propias;
    private final ModerarCalificacionPort moderar;

    public CalificacionController(
            CalificarPort calificar,
            ConsultarCalificacionesPort consultar,
            CalificacionPropiaPort propias,
            ModerarCalificacionPort moderar
    ) {
        this.calificar = Objects.requireNonNull(calificar, "calificar");
        this.consultar = Objects.requireNonNull(consultar, "consultar");
        this.propias = Objects.requireNonNull(propias, "propias");
        this.moderar = Objects.requireNonNull(moderar, "moderar");
    }

    @PostMapping("/api/v1/calificaciones")
    @PreAuthorize(PARTICIPANTES)
    public ResponseEntity<CalificacionCreada> calificar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CalificarRequest body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        Calificacion calificacion = calificar.calificar(usuario(jwt), rol(jwt), new CalificarPort.NuevaCalificacion(
                body.pedidoId(), body.tipoEntidad(), body.entidadId(), body.puntaje().intValueExact(),
                body.comentario()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CalificacionCreada.de(calificacion));
    }

    /** A quién puede calificar el usuario en un pedido entregado (por pedido o, para el domiciliario, por domicilio). */
    @GetMapping("/api/v1/calificaciones/opciones")
    @PreAuthorize(PARTICIPANTES)
    public ResponseEntity<CalificarPort.Opciones> opciones(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        Map<String, String[]> parametros = request.getParameterMap();
        for (String nombre : parametros.keySet()) {
            if (!Set.of("pedidoId", "domicilioId").contains(nombre)) {
                throw invalido(nombre, "Filtro no permitido.");
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(calificar.opciones(usuario(jwt), rol(jwt),
                uuid(parametros, "pedidoId"), uuid(parametros, "domicilioId")));
    }

    /** RF-02 en listados: promedio de varios productos o emprendimientos ({@code ids} separados por comas). */
    @GetMapping("/api/v1/calificaciones/resumenes")
    @PreAuthorize(PARTICIPANTES)
    public ResponseEntity<Map<String, Object>> resumenes(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        Map<String, String[]> parametros = request.getParameterMap();
        for (String nombre : parametros.keySet()) {
            if (!Set.of("tipoEntidad", "ids").contains(nombre)) {
                throw invalido(nombre, "Filtro no permitido.");
            }
        }
        TipoEntidad tipo = enumValor(parametros, "tipoEntidad", TipoEntidad.class);
        String texto = valor(parametros, "ids");
        if (tipo == null || texto == null) {
            throw invalido(tipo == null ? "tipoEntidad" : "ids", "Obligatorio.");
        }
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (String parte : texto.split(",")) {
            try {
                ids.add(UUID.fromString(parte.strip()));
            } catch (IllegalArgumentException e) {
                throw invalido("ids", "Identificador inválido.");
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("resumenes", consultar.resumenes(usuario(jwt), rol(jwt), tipo, ids)));
    }

    private static UUID uuid(Map<String, String[]> parametros, String nombre) {
        String valor = valor(parametros, nombre);
        if (valor == null) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw invalido(nombre, "Identificador inválido.");
        }
    }

    /** Una ruta con dos ramas: los participantes (RF-02) y el administrador (RF-03, RF-06). */
    @GetMapping("/api/v1/calificaciones")
    public ResponseEntity<?> listar(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        RolUsuario rol = rol(jwt);
        boolean admin = rol == RolUsuario.ADMINISTRADOR;
        Map<String, String[]> parametros = request.getParameterMap();
        Set<String> permitidos = admin ? FILTROS_ADMIN : FILTROS;
        for (String nombre : parametros.keySet()) {
            if (!permitidos.contains(nombre)) {
                throw invalido(nombre, "Filtro no permitido.");
            }
        }
        Consulta consulta = consulta(parametros);
        Object body = admin ? consultar.supervisar(consulta) : consultar.consultar(usuario(jwt), rol, consulta);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** RF-04. Un campo ausente no cambia; {@code null} o vacío se rechaza. */
    @PatchMapping("/api/v1/calificaciones/{id}")
    @PreAuthorize(PARTICIPANTES)
    public ResponseEntity<CalificacionEditada> editar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        if (body == null || body.isEmpty()) {
            throw invalido("puntaje", "Envía el puntaje, el comentario o ambos.");
        }
        for (String campo : body.keySet()) {
            if (!CAMPOS_EDICION.contains(campo)) {
                throw invalido(campo, "Campo no permitido.");
            }
        }
        Integer puntaje = body.containsKey("puntaje") ? puntaje(body.get("puntaje")) : null;
        String comentario = body.containsKey("comentario") ? texto(body.get("comentario"), "comentario") : null;
        Calificacion editada = propias.editar(usuario(jwt), id, puntaje, comentario);
        return ResponseEntity.ok(new CalificacionEditada(editada.id(), editada.puntaje(), editada.comentario(),
                editada.estado().name(), editada.updatedAt()));
    }

    /** RF-05. */
    @PostMapping("/api/v1/calificaciones/{id}/eliminar")
    @PreAuthorize(PARTICIPANTES)
    public ResponseEntity<CalificacionEstado> eliminar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        sinQuery(request);
        Calificacion eliminada = propias.eliminar(usuario(jwt), id);
        return ResponseEntity.ok(new CalificacionEstado(eliminada.id(), eliminada.estado().name()));
    }

    /** RF-06 FR-001. */
    @PostMapping("/api/v1/calificaciones/{id}/eliminar-moderacion")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<CalificacionEstado> eliminarModeracion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) MotivoRequest body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        Calificacion eliminada = moderar.eliminar(usuario(jwt), id, body == null ? null : body.motivo());
        return ResponseEntity.ok(new CalificacionEstado(eliminada.id(), eliminada.estado().name()));
    }

    /** RF-06 FR-002. */
    @PatchMapping("/api/v1/calificaciones/{id}/moderar-texto")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<TextoModerado> moderarTexto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) ModerarTextoRequest body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        if (body == null) {
            throw invalido("comentario", "Envía el nuevo texto y el motivo.");
        }
        Calificacion editada = moderar.editarTexto(usuario(jwt), id, body.comentario(), body.motivo());
        return ResponseEntity.ok(new TextoModerado(editada.id(), editada.estado().name(), editada.comentario()));
    }

    private static Consulta consulta(Map<String, String[]> parametros) {
        TipoEntidad tipo = enumValor(parametros, "tipoEntidad", TipoEntidad.class);
        UUID entidadId = null;
        String entidad = valor(parametros, "entidadId");
        if (entidad != null) {
            try {
                entidadId = UUID.fromString(entidad);
            } catch (IllegalArgumentException e) {
                throw invalido("entidadId", "Identificador inválido.");
            }
        }
        EstadoCalificacion estado = enumValor(parametros, "estado", EstadoCalificacion.class);
        int page = entero(parametros, "page", 0);
        int size = entero(parametros, "size", 20);
        if (page < 0) {
            throw invalido("page", "La página no puede ser negativa.");
        }
        if (size < 1 || size > 100) {
            throw invalido("size", "El tamaño debe estar entre 1 y 100.");
        }
        String sortCampo = null;
        boolean ascendente = false;
        String sort = valor(parametros, "sort");
        if (sort != null) {
            String[] partes = sort.split(",", -1);
            if (partes.length > 2 || !Set.of("createdAt", "puntaje").contains(partes[0])) {
                throw invalido("sort", "Ordena por createdAt o puntaje.");
            }
            sortCampo = partes[0];
            if (partes.length == 2) {
                if (!Set.of("asc", "desc").contains(partes[1].toLowerCase())) {
                    throw invalido("sort", "La dirección debe ser asc o desc.");
                }
                ascendente = "asc".equalsIgnoreCase(partes[1]);
            }
        }
        return new Consulta(tipo, entidadId, estado, page, size, sortCampo, ascendente);
    }

    private static <E extends Enum<E>> E enumValor(Map<String, String[]> parametros, String nombre, Class<E> tipo) {
        String valor = valor(parametros, nombre);
        if (valor == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw invalido(nombre, "Valor inválido.");
        }
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

    private static Integer puntaje(Object valor) {
        if (valor instanceof Integer entero) {
            return entero;
        }
        if (valor instanceof Number numero) {
            try {
                return new BigDecimal(numero.toString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw invalido("puntaje", "El puntaje debe ser un número entero de 1 a 5.");
            }
        }
        throw invalido("puntaje", "El puntaje debe ser un número entero de 1 a 5.");
    }

    private static String texto(Object valor, String campo) {
        if (!(valor instanceof String texto) || texto.isBlank()) {
            throw invalido(campo, "El comentario debe tener entre 1 y 300 caracteres.");
        }
        return texto;
    }

    private static UUID usuario(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static RolUsuario rol(Jwt jwt) {
        return RolUsuario.de(jwt.getClaimAsString("rol"));
    }

    private static void sinQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }

    private static DomainException invalido(String campo, String mensaje) {
        return new DomainException("VALIDATION_ERROR", mensaje, Map.of("fields", Map.of(campo, mensaje)));
    }

    public record CalificarRequest(
            @NotNull UUID pedidoId,
            @NotNull TipoEntidad tipoEntidad,
            @NotNull UUID entidadId,
            @NotNull @DecimalMin("1") @DecimalMax("5") @Digits(integer = 1, fraction = 0) BigDecimal puntaje,
            @Size(max = Calificacion.COMENTARIO_MAXIMO) String comentario
    ) {}

    public record MotivoRequest(String motivo) {}

    public record ModerarTextoRequest(String comentario, String motivo) {}

    public record CalificacionCreada(
            UUID id,
            UUID pedidoId,
            String tipoEntidad,
            UUID entidadId,
            int puntaje,
            String comentario,
            String estado,
            Instant createdAt
    ) {
        static CalificacionCreada de(Calificacion c) {
            return new CalificacionCreada(c.id(), c.pedidoId(), c.tipoEntidad().name(), c.entidadId(), c.puntaje(),
                    c.comentario(), c.estado().name(), c.createdAt());
        }
    }

    public record CalificacionEditada(UUID id, int puntaje, String comentario, String estado, Instant updatedAt) {}

    public record CalificacionEstado(UUID id, String estado) {}

    public record TextoModerado(UUID id, String estado, String comentario) {}
}
