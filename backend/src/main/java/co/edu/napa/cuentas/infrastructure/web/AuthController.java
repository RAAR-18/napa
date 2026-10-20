package co.edu.napa.cuentas.infrastructure.web;

import co.edu.napa.cuentas.application.usecase.AuthTokens;
import co.edu.napa.cuentas.application.usecase.AuthUseCase;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registrar(
            @Valid @RequestBody RegistroRequest request,
            @RequestHeader("X-Device-Id") String deviceId,
            HttpServletRequest servletRequest
    ) {
        AuthUseCase.RegistroResultado result = authUseCase.registrar(
                request.nombre(),
                request.celular(),
                request.correo(),
                request.password(),
                request.confirmacionPassword(),
                request.rol(),
                deviceId,
                servletRequest.getRemoteAddr()
        );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(new RegistroResponse(result.usuarioId(), result.estado(), true));
    }

    @PostMapping("/verificaciones-celular")
    public ResponseEntity<Map<String, String>> solicitarVerificacion(
            @Valid @RequestBody SolicitarVerificacionRequest request,
            HttpServletRequest servletRequest
    ) {
        authUseCase.solicitarVerificacion(
                request.celular(),
                request.proposito(),
                servletRequest.getRemoteAddr()
        );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("estado", "SOLICITADA"));
    }

    @PostMapping("/verificaciones-celular/confirmar")
    public ResponseEntity<AuthTokens> confirmarRegistro(
            @Valid @RequestBody ConfirmarRegistroRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authUseCase.confirmarRegistro(request.celular(), request.codigo(), deviceId));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokens> iniciarSesion(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader("X-Device-Id") String deviceId,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authUseCase.iniciarSesion(
                        request.identificador(),
                        request.password(),
                        deviceId,
                        servletRequest.getRemoteAddr()
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokens> renovarSesion(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authUseCase.renovarSesion(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(@AuthenticationPrincipal Jwt jwt) {
        authUseCase.cerrarSesion(
                UUID.fromString(jwt.getId()),
                ((Number) jwt.getClaim("sv")).longValue()
        );
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @PostMapping("/cambio-contrasena")
    public ResponseEntity<Void> cambiarContrasena(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CambioContrasenaRequest request
    ) {
        authUseCase.cambiarContrasena(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getId()),
                request.contrasenaActual(),
                request.contrasenaNueva()
        );
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @PostMapping("/recuperar-contrasena")
    public ResponseEntity<Void> recuperarContrasena(@Valid @RequestBody RecuperarContrasenaRequest request) {
        authUseCase.recuperarContrasena(
                request.celular(),
                request.codigo(),
                request.contrasenaNueva()
        );
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    public record RegistroRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank String celular,
            @Email @Size(max = 254) String correo,
            @NotNull String password,
            @NotNull String confirmacionPassword,
            @NotNull RolUsuario rol
    ) {
    }

    public record RegistroResponse(UUID usuarioId, String estado, boolean verificationRequired) {
    }

    public record SolicitarVerificacionRequest(
            @NotBlank String celular,
            @NotNull EstadoVerificacion proposito
    ) {
    }

    public record ConfirmarRegistroRequest(
            @NotBlank String celular,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String codigo
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String identificador,
            @NotNull @Size(max = 512) String password
    ) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {
    }

    public record CambioContrasenaRequest(
            @NotNull String contrasenaActual,
            @NotNull String contrasenaNueva
    ) {
    }

    public record RecuperarContrasenaRequest(
            @NotBlank String celular,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String codigo,
            @NotNull String contrasenaNueva
    ) {
    }
}
