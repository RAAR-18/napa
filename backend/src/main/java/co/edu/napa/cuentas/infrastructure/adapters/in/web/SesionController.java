package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.CerrarSesionPort;
import co.edu.napa.cuentas.application.ports.in.IniciarSesionPort;
import co.edu.napa.cuentas.application.ports.in.RefrescarSesionPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** RF-10: inicio, renovación y cierre de sesión. */
@RestController
@RequestMapping("/api/v1/auth")
public class SesionController {
    private final IniciarSesionPort iniciarSesion;
    private final RefrescarSesionPort refrescarSesion;
    private final CerrarSesionPort cerrarSesion;

    public SesionController(
            IniciarSesionPort iniciarSesion,
            RefrescarSesionPort refrescarSesion,
            CerrarSesionPort cerrarSesion
    ) {
        this.iniciarSesion = iniciarSesion;
        this.refrescarSesion = refrescarSesion;
        this.cerrarSesion = cerrarSesion;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokens> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader("X-Device-Id") String deviceId,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(iniciarSesion.iniciarSesion(
                        request.identificador(),
                        request.password(),
                        deviceId,
                        servletRequest.getRemoteAddr()
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokens> refresh(
            @Valid @RequestBody RefreshRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(refrescarSesion.refrescar(request.refreshToken(), deviceId));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        cerrarSesion.cerrar(
                UUID.fromString(jwt.getId()),
                ((Number) jwt.getClaim("sv")).longValue()
        );
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String identificador,
            @NotNull @Size(max = 512) String password
    ) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {
    }
}