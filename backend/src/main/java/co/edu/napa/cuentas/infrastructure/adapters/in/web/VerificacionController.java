package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.BuscarCuentaPorCorreoPort;
import co.edu.napa.cuentas.application.ports.in.ConfirmarRegistroPort;
import co.edu.napa.cuentas.application.ports.in.ConsultarMensajeVerificacionPort;
import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * RF-70: reenvío del código de registro (por WhatsApp o SMS), confirmación del celular y estado del envío.
 * La recuperación de contraseña y el cambio de celular tienen sus propios endpoints (RF-09 y RF-08).
 */
@RestController
@RequestMapping("/api/v1/auth/verify-phone")
public class VerificacionController {
    private final SolicitarVerificacionPort solicitarVerificacion;
    private final ConfirmarRegistroPort confirmarRegistro;
    private final ConsultarMensajeVerificacionPort consultarMensaje;
    private final BuscarCuentaPorCorreoPort buscarPorCorreo;

    public VerificacionController(
            SolicitarVerificacionPort solicitarVerificacion,
            ConfirmarRegistroPort confirmarRegistro,
            ConsultarMensajeVerificacionPort consultarMensaje,
            BuscarCuentaPorCorreoPort buscarPorCorreo
    ) {
        this.solicitarVerificacion = solicitarVerificacion;
        this.confirmarRegistro = confirmarRegistro;
        this.consultarMensaje = consultarMensaje;
        this.buscarPorCorreo = buscarPorCorreo;
    }

    /** La cuenta se identifica por celular o, si no viene, por su correo. */
    private String celularDe(String celular, String correo) {
        if (celular != null && !celular.isBlank()) {
            return celular;
        }
        if (correo != null && !correo.isBlank()) {
            return buscarPorCorreo.celularDe(correo);
        }
        throw new DomainException("VALIDATION_ERROR", "Escribe el celular o el correo de tu cuenta.");
    }

    @PostMapping("/request")
    public ResponseEntity<EnvioResponse> solicitar(
            @Valid @RequestBody SolicitarRequest request,
            HttpServletRequest servletRequest
    ) {
        boolean porCorreo = request.celular() == null || request.celular().isBlank();
        CanalVerificacion canal = porCorreo
                ? CanalVerificacion.CORREO
                : request.canal() == null ? CanalVerificacion.WHATSAPP : request.canal();
        SolicitarVerificacionPort.EnvioSolicitado envio = solicitarVerificacion.solicitar(
                celularDe(request.celular(), request.correo()),
                PropositoVerificacion.REGISTRO,
                canal,
                servletRequest.getRemoteAddr()
        );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(new EnvioResponse(envio.mensajeId(), envio.canal().name(), envio.reenvioDisponibleEn()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<AuthTokens> confirmar(
            @Valid @RequestBody ConfirmarRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(confirmarRegistro.confirmar(
                        celularDe(request.celular(), request.correo()), request.codigo(), deviceId
                ));
    }

    @GetMapping("/mensajes/{mensajeId}")
    public ResponseEntity<MensajeResponse> consultar(@PathVariable UUID mensajeId) {
        ConsultarMensajeVerificacionPort.MensajeVista vista = consultarMensaje.consultar(mensajeId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MensajeResponse(
                        vista.mensajeId(), vista.canal().name(), vista.estado().name(), vista.smsDisponible()
                ));
    }

    public record SolicitarRequest(String celular, String correo, CanalVerificacion canal) {
    }

    public record ConfirmarRequest(
            String celular,
            String correo,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String codigo
    ) {
    }

    public record EnvioResponse(UUID mensajeId, String canal, long reenvioDisponibleEn) {
    }

    public record MensajeResponse(UUID mensajeId, String canal, String estado, boolean smsDisponible) {
    }
}