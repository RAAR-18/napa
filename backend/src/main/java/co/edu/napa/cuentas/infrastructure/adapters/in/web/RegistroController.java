package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.in.RegistrarCuentaPort;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** RF-07: registro de cuenta. */
@RestController
@RequestMapping("/api/v1/auth")
public class RegistroController {
    private final RegistrarCuentaPort registrarCuenta;

    public RegistroController(RegistrarCuentaPort registrarCuenta) {
        this.registrarCuenta = registrarCuenta;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistroResponse> registrar(
            @Valid @RequestBody RegistroRequest request,
            @RequestHeader("X-Device-Id") String deviceId,
            HttpServletRequest servletRequest
    ) {
        RegistrarCuentaPort.RegistroResultado result = registrarCuenta.registrar(
                request.nombre(),
                request.celular(),
                request.correo(),
                request.password(),
                request.confirmPassword(),
                request.rol(),
                deviceId,
                servletRequest.getRemoteAddr()
        );
        return ResponseEntity.created(URI.create("/api/v1/usuarios/me"))
                .cacheControl(CacheControl.noStore())
                .body(new RegistroResponse(result.usuarioId(), result.estado()));
    }

    public record RegistroRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank String celular,
            @Email @Size(max = 254) String correo,
            @NotNull String password,
            @NotNull String confirmPassword,
            @NotNull RolUsuario rol
    ) {
    }

    public record RegistroResponse(UUID usuarioId, String estado) {
    }
}