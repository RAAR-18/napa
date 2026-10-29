package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.ConfirmarRegistroPort;
import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionCelularPort;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * RF-70 (versión provisional hasta implementar RF-70 completo): reenvío del código de registro
 * y confirmación del celular. La recuperación de contraseña y el cambio de celular tienen sus propios endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth/verify-phone")
public class VerificacionController {
    private final SolicitarVerificacionCelularPort solicitarVerificacion;
    private final ConfirmarRegistroPort confirmarRegistro;

    public VerificacionController(
            SolicitarVerificacionCelularPort solicitarVerificacion,
            ConfirmarRegistroPort confirmarRegistro
    ) {
        this.solicitarVerificacion = solicitarVerificacion;
        this.confirmarRegistro = confirmarRegistro;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> solicitar(
            @Valid @RequestBody SolicitarRequest request,
            HttpServletRequest servletRequest
    ) {
        solicitarVerificacion.solicitar(
                request.celular(),
                EstadoVerificacion.REGISTRO,
                servletRequest.getRemoteAddr()
        );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("estado", "SOLICITADA"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<AuthTokens> confirmar(
            @Valid @RequestBody ConfirmarRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(confirmarRegistro.confirmar(request.celular(), request.codigo(), deviceId));
    }

    public record SolicitarRequest(@NotBlank String celular) {
    }

    public record ConfirmarRequest(
            @NotBlank String celular,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String codigo
    ) {
    }
}