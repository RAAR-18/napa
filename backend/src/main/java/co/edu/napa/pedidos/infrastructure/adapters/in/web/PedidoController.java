package co.edu.napa.pedidos.infrastructure.adapters.in.web;

import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.ItemSolicitud;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.Solicitud;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.UbicacionEntrega;
import co.edu.napa.pedidos.application.ports.in.RealizarPedidoPort;
import co.edu.napa.pedidos.application.ports.in.RealizarPedidoPort.PedidoCreado;
import co.edu.napa.pedidos.application.ports.in.RealizarPedidoPort.SolicitudPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class PedidoController {
    private final CotizarPedidoPort cotizaciones;
    private final RealizarPedidoPort realizarPedido;

    public PedidoController(CotizarPedidoPort cotizaciones, RealizarPedidoPort realizarPedido) {
        this.cotizaciones = cotizaciones;
        this.realizarPedido = realizarPedido;
    }

    @PostMapping("/api/v1/pedidos/cotizacion")
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<CotizarPedidoPort.Cotizacion> cotizar(
            @Valid @RequestBody CotizarRequest body,
            HttpServletRequest request
    ) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(cotizaciones.cotizar(solicitud(body.emprendimientoId(), body.modalidad(), body.items(),
                        body.ubicacionEntrega(), body.reserva())));
    }

    @PostMapping("/api/v1/pedidos")
    @PreAuthorize("hasAuthority('ROLE_CLIENTE')")
    public ResponseEntity<PedidoCreado> realizar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RealizarPedidoRequest body,
            HttpServletRequest request
    ) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
        PedidoCreado creado = realizarPedido.realizar(UUID.fromString(jwt.getSubject()),
                new SolicitudPedido(solicitud(body.emprendimientoId(), body.modalidad(), body.items(),
                        body.ubicacionEntrega(), body.reserva()), body.medioPago(), body.totalEsperado()));
        return ResponseEntity.created(URI.create("/api/v1/pedidos/" + creado.id()))
                .cacheControl(CacheControl.noStore()).body(creado);
    }

    private Solicitud solicitud(
            UUID emprendimientoId,
            ModalidadEntrega modalidad,
            List<ItemRequest> items,
            UbicacionRequest ubicacionRequest,
            Instant reserva
    ) {
        UbicacionEntrega ubicacion = ubicacionRequest == null ? null : new UbicacionEntrega(
                ubicacionRequest.latitud(), ubicacionRequest.longitud(),
                ubicacionRequest.direccion(), ubicacionRequest.referencia());
        return new Solicitud(emprendimientoId, modalidad,
                items.stream().map(item -> new ItemSolicitud(item.productoId(), item.cantidad())).toList(),
                ubicacion, reserva);
    }

    public record CotizarRequest(
            @NotNull java.util.UUID emprendimientoId,
            @NotNull ModalidadEntrega modalidad,
            @NotEmpty @Size(max = 20) List<@NotNull @Valid ItemRequest> items,
            @Valid UbicacionRequest ubicacionEntrega,
            Instant reserva
    ) {}

    public record RealizarPedidoRequest(
            @NotNull UUID emprendimientoId,
            @NotNull ModalidadEntrega modalidad,
            @NotEmpty @Size(max = 20) List<@NotNull @Valid ItemRequest> items,
            @Valid UbicacionRequest ubicacionEntrega,
            Instant reserva,
            @NotBlank String medioPago,
            @PositiveOrZero Long totalEsperado
    ) {}

    public record ItemRequest(
            @NotNull java.util.UUID productoId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("20")
            @Digits(integer = 2, fraction = 2) BigDecimal cantidad
    ) {}

    public record UbicacionRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud,
            @NotBlank @Size(max = 300) String direccion,
            @Size(max = 300) String referencia
    ) {}
}
