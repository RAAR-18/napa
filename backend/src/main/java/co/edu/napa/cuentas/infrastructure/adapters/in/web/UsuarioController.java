package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.in.AdministrarUsuariosPort;
import co.edu.napa.cuentas.application.ports.in.GestionarCuentaPropiaPort;
import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
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

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** RF-08 y RF-11 (la propia cuenta); RF-66 a RF-69 (administración de usuarios). */
@RestController
public class UsuarioController {
    private static final Set<String> CAMPOS_PROPIOS = Set.of("nombre", "ubicacion");
    private static final Set<String> CAMPOS_ADMIN = Set.of("nombre", "celular", "correo", "rol", "ubicacion");
    private static final Set<String> CAMPOS_UBICACION = Set.of("latitud", "longitud", "direccion", "referencia");
    private static final Set<String> FILTROS = Set.of("rol", "estado", "q", "page", "size", "sort");
    private static final Set<String> ROLES = Set.of(
            "CLIENTE", "VENDEDOR_AMBULANTE", "VENDEDOR_PUNTO_FIJO", "DOMICILIARIO", "ADMINISTRADOR");
    private static final Set<String> ESTADOS = Set.of("PENDIENTE_VERIFICACION", "ACTIVA", "ELIMINADA");

    private final GestionarCuentaPropiaPort propia;
    private final AdministrarUsuariosPort administrar;

    public UsuarioController(GestionarCuentaPropiaPort propia, AdministrarUsuariosPort administrar) {
        this.propia = Objects.requireNonNull(propia, "propia");
        this.administrar = Objects.requireNonNull(administrar, "administrar");
    }

    @GetMapping("/api/v1/usuarios/me")
    public ResponseEntity<PerfilUsuario> perfil(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        sinQuery(request);
        return sinCache(propia.perfil(usuario(jwt)));
    }

    @PatchMapping("/api/v1/usuarios/me")
    public ResponseEntity<PerfilUsuario> editarPerfil(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        Map<String, Object> campos = campos(body, CAMPOS_PROPIOS);
        return sinCache(propia.editar(usuario(jwt), texto(campos, "nombre"), ubicacion(campos)));
    }

    @PostMapping("/api/v1/usuarios/me/eliminar")
    public ResponseEntity<Void> eliminarCuenta(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        exigirConfirmacion(body);
        propia.eliminar(usuario(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/usuarios")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<AdministrarUsuariosPort.Pagina> listar(HttpServletRequest request) {
        Map<String, String[]> parametros = request.getParameterMap();
        for (String nombre : parametros.keySet()) {
            if (!FILTROS.contains(nombre)) {
                throw invalido(nombre, "Filtro no permitido.");
            }
        }
        String rol = parametro(parametros, "rol");
        Set<String> roles;
        if (rol == null) {
            roles = Set.of();
        } else if ("VENDEDOR".equals(rol)) {
            roles = Set.of("VENDEDOR_AMBULANTE", "VENDEDOR_PUNTO_FIJO");
        } else if (ROLES.contains(rol)) {
            roles = Set.of(rol);
        } else {
            throw invalido("rol", "Rol inválido.");
        }
        String estado = parametro(parametros, "estado");
        if (estado != null && !ESTADOS.contains(estado)) {
            throw invalido("estado", "Estado inválido.");
        }
        String q = parametro(parametros, "q");
        if (q != null && q.length() < 2) {
            throw invalido("q", "Escribe al menos 2 caracteres.");
        }
        int page = entero(parametros, "page", 0);
        int size = entero(parametros, "size", 20);
        if (page < 0) {
            throw invalido("page", "La página no puede ser negativa.");
        }
        if (size < 1 || size > 100) {
            throw invalido("size", "El tamaño debe estar entre 1 y 100.");
        }
        String sortCampo = "createdAt";
        boolean ascendente = false;
        String sort = parametro(parametros, "sort");
        if (sort != null) {
            String[] partes = sort.split(",", -1);
            if (partes.length > 2 || !Set.of("nombre", "createdAt").contains(partes[0])
                    || (partes.length == 2 && !Set.of("asc", "desc").contains(partes[1].toLowerCase()))) {
                throw invalido("sort", "Ordena por nombre o createdAt.");
            }
            sortCampo = partes[0];
            ascendente = partes.length == 2 ? "asc".equalsIgnoreCase(partes[1]) : "nombre".equals(sortCampo);
        }
        AdministrarUsuariosPort.Pagina pagina = administrar.listar(
                new AdministrarUsuariosPort.Filtro(roles, estado, q), page, size, sortCampo, ascendente);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pagina);
    }

    @GetMapping("/api/v1/usuarios/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<PerfilUsuario> consultar(@PathVariable UUID id, HttpServletRequest request) {
        sinQuery(request);
        return sinCache(administrar.consultar(id));
    }

    @PatchMapping("/api/v1/usuarios/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<PerfilUsuario> editar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        Map<String, Object> campos = campos(body, CAMPOS_ADMIN);
        AdministrarUsuariosPort.Cambios cambios = new AdministrarUsuariosPort.Cambios(
                texto(campos, "nombre"), texto(campos, "celular"), texto(campos, "correo"), texto(campos, "rol"),
                ubicacion(campos));
        return sinCache(administrar.editar(usuario(jwt), id, cambios));
    }

    @PostMapping("/api/v1/usuarios/{id}/eliminar")
    @PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        exigirConfirmacion(body);
        Object forzar = body.get("forzar");
        if (forzar != null && !(forzar instanceof Boolean)) {
            throw invalido("forzar", "Debe ser verdadero o falso.");
        }
        administrar.eliminar(usuario(jwt), id, Boolean.TRUE.equals(forzar));
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> campos(Map<String, Object> body, Set<String> permitidos) {
        if (body == null || body.isEmpty()) {
            throw invalido("nombre", "Envía al menos un campo para actualizar.");
        }
        for (String campo : body.keySet()) {
            if (!permitidos.contains(campo)) {
                throw invalido(campo, "Este campo no se puede editar aquí.");
            }
            if (body.get(campo) == null) {
                throw invalido(campo, "El valor no puede ser nulo.");
            }
        }
        return body;
    }

    private static String texto(Map<String, Object> campos, String campo) {
        Object valor = campos.get(campo);
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof String texto)) {
            throw invalido(campo, "Debe ser un texto.");
        }
        return texto;
    }

    private static PerfilUsuario.Ubicacion ubicacion(Map<String, Object> campos) {
        Object valor = campos.get("ubicacion");
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof Map<?, ?> datos)) {
            throw invalido("ubicacion", "La ubicación no es válida.");
        }
        for (Object clave : datos.keySet()) {
            if (!CAMPOS_UBICACION.contains(String.valueOf(clave))) {
                throw invalido("ubicacion." + clave, "Campo no permitido.");
            }
        }
        if (!(datos.get("latitud") instanceof Number latitud)) {
            throw invalido("ubicacion.latitud", "La latitud es obligatoria.");
        }
        if (!(datos.get("longitud") instanceof Number longitud)) {
            throw invalido("ubicacion.longitud", "La longitud es obligatoria.");
        }
        Object direccion = datos.get("direccion");
        Object referencia = datos.get("referencia");
        if ((direccion != null && !(direccion instanceof String)) || (referencia != null && !(referencia instanceof String))) {
            throw invalido("ubicacion", "La dirección y la referencia deben ser texto.");
        }
        return new PerfilUsuario.Ubicacion(latitud.doubleValue(), longitud.doubleValue(), (String) direccion,
                (String) referencia);
    }

    private static void exigirConfirmacion(Map<String, Object> body) {
        if (body == null || !Boolean.TRUE.equals(body.get("confirmar"))) {
            throw invalido("confirmar", "Confirma la eliminación.");
        }
    }

    private static String parametro(Map<String, String[]> parametros, String nombre) {
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
        String valor = parametro(parametros, nombre);
        if (valor == null) {
            return defecto;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw invalido(nombre, "Debe ser un número entero.");
        }
    }

    private static <T> ResponseEntity<T> sinCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static UUID usuario(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static void sinQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }

    private static DomainException invalido(String campo, String mensaje) {
        return new DomainException("VALIDATION_ERROR", mensaje, Map.of("fields", Map.of(campo, mensaje)));
    }
}
