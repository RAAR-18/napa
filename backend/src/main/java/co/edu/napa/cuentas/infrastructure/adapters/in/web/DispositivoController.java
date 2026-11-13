package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.in.RegistrarDispositivoPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RF-38: registro del token push del dispositivo. {@code POST} es idempotente por (usuario, token), así que está
 * exento de {@code Idempotency-Key} (spec-general).
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class DispositivoController {
    private final RegistrarDispositivoPort dispositivos;

    public DispositivoController(RegistrarDispositivoPort dispositivos) {
        this.dispositivos = dispositivos;
    }

    @PostMapping("/api/v1/dispositivos")
    public ResponseEntity<Void> registrar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Map<String, Object> campos = campos(body, Set.of("token", "plataforma"));
        dispositivos.registrar(UUID.fromString(jwt.getSubject()), texto(campos, "token"), texto(campos, "plataforma"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/dispositivos")
    public ResponseEntity<Void> darDeBaja(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Map<String, Object> campos = campos(body, Set.of("token"));
        dispositivos.darDeBaja(UUID.fromString(jwt.getSubject()), texto(campos, "token"));
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> campos(Map<String, Object> body, Set<String> permitidos) {
        if (body == null) {
            throw new DomainException("VALIDATION_ERROR", "Envía el token del dispositivo.");
        }
        if (!permitidos.containsAll(body.keySet())) {
            throw new DomainException("VALIDATION_ERROR", "La solicitud contiene campos no permitidos.");
        }
        return body;
    }

    private static String texto(Map<String, Object> body, String campo) {
        Object value = body.get(campo);
        if (value != null && !(value instanceof String)) {
            String message = "El campo debe ser texto.";
            throw new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(campo, message)));
        }
        return (String) value;
    }
}
