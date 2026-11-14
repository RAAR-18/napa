package co.edu.napa.pedidos.infrastructure.adapters.in.web;

import co.edu.napa.pedidos.application.ports.in.ConsultarDomiciliarioPedidoPort;
import co.edu.napa.pedidos.application.ports.in.ConsultarDomiciliarioPedidoPort.DomiciliarioDePedido;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** RF-27: datos básicos del domiciliario de un pedido, para su cliente o su vendedor de punto fijo. */
@RestController
public class DomiciliarioPedidoController {
    private final ConsultarDomiciliarioPedidoPort domiciliario;

    public DomiciliarioPedidoController(ConsultarDomiciliarioPedidoPort domiciliario) {
        this.domiciliario = domiciliario;
    }

    @GetMapping("/api/v1/pedidos/{pedidoId}/domiciliario")
    @PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<DomiciliarioDePedido> consultar(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        boolean cliente = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CLIENTE".equals(authority.getAuthority()));
        DomiciliarioDePedido body = cliente
                ? domiciliario.consultarComoCliente(usuarioId, pedidoId)
                : domiciliario.consultarComoVendedor(usuarioId, pedidoId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
