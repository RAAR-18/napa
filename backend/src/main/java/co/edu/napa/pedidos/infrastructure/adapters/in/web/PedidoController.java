package co.edu.napa.pedidos.infrastructure.adapters.in.web;

import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.ItemSolicitud;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.Solicitud;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.UbicacionEntrega;
import co.edu.napa.pedidos.application.ports.in.RealizarPedidoPort;
import co.edu.napa.pedidos.application.ports.in.RealizarPedidoPort.PedidoCreado;
import co.edu.napa.pedidos.application.ports.in.RealizarPedidoPort.SolicitudPedido;
import co.edu.napa.pedidos.application.ports.in.ResponderPedidoPort;
import co.edu.napa.pedidos.application.ports.in.ResponderPedidoPort.PedidoAceptado;
import co.edu.napa.pedidos.application.ports.in.ResponderPedidoPort.PedidoRechazado;
import co.edu.napa.pedidos.application.ports.in.ConsultarPedidoVendedorPort;
import co.edu.napa.pedidos.application.ports.in.ConfirmarPagoDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.ConfirmarPagoDomicilioPort.PagoDomicilioConfirmado;
import co.edu.napa.pedidos.application.ports.in.ListarPedidosVendedorPort;
import co.edu.napa.pedidos.application.ports.in.PedidoVendedorDetalle;
import co.edu.napa.pedidos.application.ports.in.PedidosVendedorPagina;
import co.edu.napa.pedidos.application.ports.out.FiltroPedidosVendedor;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class PedidoController {
    private static final Set<String> LIST_FILTERS = Set.of(
            "estado", "modalidad", "reserva", "desde", "hasta", "page", "size", "sort"
    );
    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");

    private final CotizarPedidoPort cotizaciones;
    private final RealizarPedidoPort realizarPedido;
    private final ResponderPedidoPort responderPedido;
    private final ListarPedidosVendedorPort listarPedidosVendedor;
    private final ConsultarPedidoVendedorPort consultarPedidoVendedor;
    private final ConfirmarPagoDomicilioPort confirmarPagoDomicilio;

    public PedidoController(
            CotizarPedidoPort cotizaciones,
            RealizarPedidoPort realizarPedido,
            ResponderPedidoPort responderPedido,
            ListarPedidosVendedorPort listarPedidosVendedor,
            ConsultarPedidoVendedorPort consultarPedidoVendedor,
            ConfirmarPagoDomicilioPort confirmarPagoDomicilio
    ) {
        this.cotizaciones = cotizaciones;
        this.realizarPedido = realizarPedido;
        this.responderPedido = responderPedido;
        this.listarPedidosVendedor = listarPedidosVendedor;
        this.consultarPedidoVendedor = consultarPedidoVendedor;
        this.confirmarPagoDomicilio = confirmarPagoDomicilio;
    }

    @GetMapping("/api/v1/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PedidosVendedorPagina> listarPedidos(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        Map<String, String[]> parameters = request.getParameterMap();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                listarPedidosVendedor.listar(
                        UUID.fromString(jwt.getSubject()),
                        parseListFilter(parameters),
                        integerValue(parameters, "page", 0),
                        integerValue(parameters, "size", 20)));
    }

    @GetMapping("/api/v1/pedidos/{pedidoId}")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PedidoVendedorDetalle> consultarPedido(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(consultarPedidoVendedor.consultar(UUID.fromString(jwt.getSubject()), pedidoId));
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

    @PostMapping("/api/v1/pedidos/{pedidoId}/aceptar")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PedidoAceptado> aceptar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(responderPedido.aceptar(pedidoId, UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/rechazar")
    @PreAuthorize("hasAnyAuthority('ROLE_VENDEDOR_AMBULANTE', 'ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PedidoRechazado> rechazar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(responderPedido.rechazar(pedidoId, UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/pago/confirmar")
    @PreAuthorize("hasAuthority('ROLE_VENDEDOR_PUNTO_FIJO')")
    public ResponseEntity<PagoDomicilioConfirmado> confirmarPago(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID pedidoId,
            HttpServletRequest request
    ) {
        validarSinQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(confirmarPagoDomicilio.confirmar(pedidoId, UUID.fromString(jwt.getSubject())));
    }

    private void validarSinQuery(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "Esta operación no admite parámetros de consulta.");
        }
    }

    private FiltroPedidosVendedor parseListFilter(Map<String, String[]> parameters) {
        for (String parameter : parameters.keySet()) {
            if (!LIST_FILTERS.contains(parameter)) {
                throw validationError(parameter, "El filtro no está permitido.");
            }
        }
        EstadoPedido estado = enumValue(parameters, "estado", EstadoPedido.class);
        ModalidadEntrega modalidad = enumValue(parameters, "modalidad", ModalidadEntrega.class);
        Boolean reserva = booleanValue(parameters, "reserva");
        LocalDate fromDate = dateValue(parameters, "desde");
        LocalDate throughDate = dateValue(parameters, "hasta");
        if (fromDate != null && throughDate != null && fromDate.isAfter(throughDate)) {
            throw validationError("desde", "La fecha inicial no puede ser posterior a la fecha final.");
        }

        String sort = value(parameters, "sort");
        boolean ascending = false;
        if (sort != null) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !parts[0].equals("createdAt")) {
                throw validationError("sort", "Usa createdAt,asc o createdAt,desc.");
            }
            ascending = switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "asc" -> true;
                case "desc" -> false;
                default -> throw validationError("sort", "El orden solicitado no es válido.");
            };
        }
        Instant from;
        Instant until;
        try {
            from = fromDate == null ? null : fromDate.atStartOfDay(COLOMBIA_ZONE).toInstant();
            until = throughDate == null ? null
                    : throughDate.plusDays(1).atStartOfDay(COLOMBIA_ZONE).toInstant();
        } catch (DateTimeException exception) {
            throw validationError("hasta", "La fecha está fuera del rango permitido.");
        }
        return new FiltroPedidosVendedor(estado, modalidad, reserva, from, until, ascending);
    }

    private String value(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        if (values == null) {
            return null;
        }
        if (values.length != 1 || values[0] == null || values[0].isBlank()) {
            throw validationError(name, "El parámetro debe tener un único valor no vacío.");
        }
        return values[0];
    }

    private <T extends Enum<T>> T enumValue(Map<String, String[]> parameters, String name, Class<T> type) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw validationError(name, "El valor del filtro no es válido.");
        }
    }

    private Boolean booleanValue(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw validationError(name, "El valor debe ser true o false.");
        };
    }

    private LocalDate dateValue(Map<String, String[]> parameters, String name) {
        String value = value(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw validationError(name, "La fecha debe usar el formato ISO-8601 (yyyy-MM-dd).");
        }
    }

    private int integerValue(Map<String, String[]> parameters, String name, int defaultValue) {
        String value = value(parameters, name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw validationError(name, "El parámetro debe ser un número entero.");
        }
    }

    private DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of(
                "fields", Map.of(field, message)
        ));
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
