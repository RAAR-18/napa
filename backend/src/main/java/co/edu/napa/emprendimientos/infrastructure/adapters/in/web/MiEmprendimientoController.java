package co.edu.napa.emprendimientos.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.GestionarEmprendimientoPort;
import co.edu.napa.emprendimientos.application.ports.in.GestionarEmprendimientoPort.BaseEdicion;
import co.edu.napa.emprendimientos.application.ports.in.GestionarEmprendimientoPort.Edicion;
import co.edu.napa.emprendimientos.application.ports.in.GestionarEmprendimientoPort.NuevaUbicacion;
import co.edu.napa.emprendimientos.infrastructure.adapters.in.web.EmprendimientoController.EmprendimientoResponse;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** El vendedor consulta, edita (RF-29) y elimina (RF-30) su emprendimiento activo. */
@RestController
public class MiEmprendimientoController {
    private static final String VENDEDORES = "hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')";
    private static final Set<String> CAMPOS_EDITABLES = Set.of("descripcion", "horaApertura", "horaCierre", "ubicacion", "base");
    private static final Set<String> CAMPOS_UBICACION = Set.of("latitud", "longitud", "direccion", "referencia");

    private final GestionarEmprendimientoPort gestion;

    public MiEmprendimientoController(GestionarEmprendimientoPort gestion) {
        this.gestion = gestion;
    }

    @GetMapping("/api/v1/emprendimientos/me")
    @PreAuthorize(VENDEDORES)
    public ResponseEntity<EmprendimientoResponse> consultar(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        sinQuery(request);
        return ok(EmprendimientoResponse.from(gestion.consultarPropio(usuario(jwt), rol(jwt))));
    }

    @PatchMapping("/api/v1/emprendimientos/me")
    @PreAuthorize(VENDEDORES)
    public ResponseEntity<EmprendimientoResponse> editar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        Edicion cambios = edicion(body);
        if (cambios.base() != null && (request.getHeader("Idempotency-Key") == null
                || request.getHeader("Idempotency-Key").isBlank())) {
            throw validation("Idempotency-Key", "La edición con datos originales requiere una clave de idempotencia.");
        }
        return ok(EmprendimientoResponse.from(gestion.editar(usuario(jwt), rol(jwt), cambios)));
    }

    @PostMapping("/api/v1/emprendimientos/me/eliminar")
    @PreAuthorize(VENDEDORES)
    public ResponseEntity<Void> eliminar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        sinQuery(request);
        if (body == null || !Boolean.TRUE.equals(body.get("confirmar")) || body.size() != 1) {
            throw validation("confirmar", "Confirma la eliminación.");
        }
        gestion.eliminar(usuario(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** El nombre, la categoría y el estado no se editan; cualquier campo desconocido es un error (RF-29). */
    static Edicion edicion(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw validation("emprendimiento", "Modifica al menos un campo.");
        }
        for (String campo : body.keySet()) {
            if (!CAMPOS_EDITABLES.contains(campo)) {
                throw validation(campo, "Este campo no se puede editar.");
            }
        }
        NuevaUbicacion ubicacion = null;
        if (body.get("ubicacion") != null) {
            if (!(body.get("ubicacion") instanceof Map<?, ?> datos)) {
                throw validation("ubicacion", "La ubicación no es válida.");
            }
            for (Object campo : datos.keySet()) {
                if (!CAMPOS_UBICACION.contains(String.valueOf(campo))) {
                    throw validation("ubicacion." + campo, "Este campo no se puede editar.");
                }
            }
            ubicacion = new NuevaUbicacion(numero(datos.get("latitud"), "ubicacion.latitud"),
                    numero(datos.get("longitud"), "ubicacion.longitud"),
                    texto(datos.get("direccion"), "ubicacion.direccion"),
                    texto(datos.get("referencia"), "ubicacion.referencia"));
        }
        BaseEdicion base = body.containsKey("base") ? base(body.get("base")) : null;
        if (base != null && body.containsKey("ubicacion")) {
            throw validation("ubicacion", "Los cambios de ubicación requieren el flujo en línea.");
        }
        return new Edicion(texto(body.get("descripcion"), "descripcion"), hora(body.get("horaApertura"), "horaApertura"),
                hora(body.get("horaCierre"), "horaCierre"), ubicacion, base);
    }

    private static BaseEdicion base(Object value) {
        if (!(value instanceof Map<?, ?> datos)
                || !datos.keySet().equals(Set.of("emprendimientoId", "descripcion", "horaApertura", "horaCierre"))
                || !(datos.get("emprendimientoId") instanceof String id)) {
            throw validation("base", "Indica el emprendimiento y los valores originales.");
        }
        UUID emprendimientoId;
        try {
            emprendimientoId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw validation("base.emprendimientoId", "El identificador no es válido.");
        }
        String descripcion = texto(datos.get("descripcion"), "base.descripcion");
        LocalTime apertura = hora(datos.get("horaApertura"), "base.horaApertura");
        LocalTime cierre = hora(datos.get("horaCierre"), "base.horaCierre");
        if (descripcion == null || apertura == null || cierre == null) {
            throw validation("base", "Indica la descripción y el horario originales.");
        }
        return new BaseEdicion(emprendimientoId, descripcion, apertura, cierre);
    }

    private static Double numero(Object value, String field) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        throw validation(field, "Debe ser un número.");
    }

    private static String texto(Object value, String field) {
        if (value == null) return null;
        if (value instanceof String text) return text;
        throw validation(field, "Debe ser un texto.");
    }

    private static LocalTime hora(Object value, String field) {
        String text = texto(value, field);
        if (text == null) return null;
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException exception) {
            throw validation(field, "Usa el formato HH:mm.");
        }
    }

    private static UUID usuario(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new DomainException("UNAUTHORIZED", "La sesión no contiene un identificador de usuario válido.");
        }
    }

    private static RolUsuario rol(Jwt jwt) {
        try {
            return RolUsuario.valueOf(jwt.getClaimAsString("rol"));
        } catch (RuntimeException exception) {
            throw new DomainException("FORBIDDEN", "No tienes permiso para realizar esta acción.");
        }
    }

    private static void sinQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static DomainException validation(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
