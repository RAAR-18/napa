package co.edu.napa.pedidos.infrastructure.adapters.in.web;

import co.edu.napa.pedidos.application.ports.in.ConsultarClientePedidoPort;
import co.edu.napa.pedidos.application.ports.in.ContactarDesdePedidoPort;
import co.edu.napa.pedidos.application.ports.in.ContactarDesdePedidoPort.CanalContacto;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/** RF-49: contacto del cliente con el vendedor o el domiciliario de su pedido. */
@RestController
public class ContactoPedidoController {
    private final ContactarDesdePedidoPort contactar;
    private final ConsultarClientePedidoPort clientes;

    public ContactoPedidoController(ContactarDesdePedidoPort contactar, ConsultarClientePedidoPort clientes) {
        this.contactar = Objects.requireNonNull(contactar, "contactar");
        this.clientes = Objects.requireNonNull(clientes, "clientes");
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/contactar")
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<CanalContacto> contactar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            @Valid @RequestBody ContactarRequest body,
            HttpServletRequest request
    ) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        CanalContacto canal = contactar.contactar(UUID.fromString(jwt.getSubject()), pedidoId,
                body.destinatario(), body.tipo());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(canal);
    }

    /** RF-52: datos del cliente para el vendedor del pedido. */
    @org.springframework.web.bind.annotation.GetMapping("/api/v1/pedidos/{pedidoId}/cliente")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<ConsultarClientePedidoPort.ClientePedido> cliente(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        if (!request.getParameterMap().isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(clientes.consultar(UUID.fromString(jwt.getSubject()), pedidoId));
    }

    public record ContactarRequest(
            @NotNull ContactarDesdePedidoPort.Destinatario destinatario,
            @NotNull ContactarDesdePedidoPort.Tipo tipo
    ) {}
}
