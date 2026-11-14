package co.edu.napa.pedidos.infrastructure.adapters.in.web;

import co.edu.napa.pedidos.application.ports.in.EntregaPedidoPort;
import co.edu.napa.pedidos.application.ports.in.EntregaPedidoPort.PedidoEntregado;
import co.edu.napa.pedidos.application.ports.in.EntregaPedidoPort.PedidoTransicionado;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Entrega directa del vendedor ambulante (RF-36, RF-37) y recogida en el punto fijo (RF-64, RF-65). */
@RestController
public class EntregaPedidoController {
    private static final String COBRO_REQUERIDO = "Confirma que recibiste el pago.";

    private final EntregaPedidoPort entregas;

    public EntregaPedidoController(EntregaPedidoPort entregas) {
        this.entregas = entregas;
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/iniciar-entrega")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_AMBULANTE')")
    public ResponseEntity<PedidoTransicionado> iniciarEntrega(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ok(entregas.iniciarEntregaDirecta(pedidoId, UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/confirmar-entrega")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_AMBULANTE')")
    public ResponseEntity<PedidoEntregado> confirmarEntrega(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            @RequestBody(required = false) CobroRequest body,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ok(entregas.confirmarEntregaDirecta(pedidoId, UUID.fromString(jwt.getSubject()), cobro(body)));
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/marcar-listo")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PedidoTransicionado> marcarListo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ok(entregas.marcarListoParaRecoger(pedidoId, UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/confirmar-retiro")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PedidoEntregado> confirmarRetiro(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            @RequestBody(required = false) CobroRequest body,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ok(entregas.confirmarRetiro(pedidoId, UUID.fromString(jwt.getSubject()), cobro(body)));
    }

    /** RF-37 y RF-65 FR-002: el vendedor marca que recibió el total por el medio que eligió el cliente. */
    private static boolean cobro(CobroRequest body) {
        if (body == null || !Boolean.TRUE.equals(body.cobroConfirmado())) {
            throw new DomainException("VALIDATION_ERROR", COBRO_REQUERIDO,
                    Map.of("fields", Map.of("cobroConfirmado", COBRO_REQUERIDO)));
        }
        return true;
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static void validarSinQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }

    public record CobroRequest(Boolean cobroConfirmado) {}
}
