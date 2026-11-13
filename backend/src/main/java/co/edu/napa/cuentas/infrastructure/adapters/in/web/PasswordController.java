package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.CambiarContrasenaPort;
import co.edu.napa.cuentas.application.ports.in.ConfirmarRecuperacionPort;
import co.edu.napa.cuentas.application.ports.in.SolicitarRecuperacionPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** RF-09: cambio de contraseña y recuperación con código. */
@RestController
@RequestMapping("/api/v1/auth/password")
public class PasswordController {
    private final CambiarContrasenaPort cambiarContrasena;
    private final SolicitarRecuperacionPort solicitarRecuperacion;
    private final ConfirmarRecuperacionPort confirmarRecuperacion;

    public PasswordController(
            CambiarContrasenaPort cambiarContrasena,
            SolicitarRecuperacionPort solicitarRecuperacion,
            ConfirmarRecuperacionPort confirmarRecuperacion
    ) {
        this.cambiarContrasena = cambiarContrasena;
        this.solicitarRecuperacion = solicitarRecuperacion;
        this.confirmarRecuperacion = confirmarRecuperacion;
    }

    @PostMapping("/change")
    public ResponseEntity<AuthTokens> cambiar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CambioRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(cambiarContrasena.cambiar(
                        UUID.fromString(jwt.getSubject()),
                        UUID.fromString(jwt.getId()),
                        request.passwordActual(),
                        request.passwordNueva()
                ));
    }

    @PostMapping("/recover/request")
    public ResponseEntity<Map<String, String>> solicitarRecuperacion(
            @Valid @RequestBody RecuperacionSolicitudRequest request,
            HttpServletRequest servletRequest
    ) {
        solicitarRecuperacion.solicitar(request.celular(), servletRequest.getRemoteAddr());
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("estado", "SOLICITADA"));
    }

    @PostMapping("/recover/confirm")
    public ResponseEntity<AuthTokens> confirmarRecuperacion(
            @Valid @RequestBody RecuperacionConfirmacionRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(confirmarRecuperacion.confirmar(
                        request.celular(),
                        request.codigo(),
                        request.passwordNueva(),
                        deviceId
                ));
    }

    public record CambioRequest(
            @NotNull String passwordActual,
            @NotNull String passwordNueva
    ) {
    }

    public record RecuperacionSolicitudRequest(@NotBlank String celular) {
    }

    public record RecuperacionConfirmacionRequest(
            @NotBlank String celular,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String codigo,
            @NotNull String passwordNueva
    ) {
    }
}