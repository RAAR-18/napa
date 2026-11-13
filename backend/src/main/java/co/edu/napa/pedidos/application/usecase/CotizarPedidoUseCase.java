package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort;
import co.edu.napa.pedidos.application.ports.in.CalcularTarifaDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.Item;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.PagoDomicilio;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.Solicitud;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.productos.application.ports.in.ConsultarDisponibilidadPort;
import co.edu.napa.productos.application.ports.in.ConsultarDisponibilidadPort.ProductoDisponible;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class CotizarPedidoUseCase implements CotizarPedidoPort {
    private static final BigDecimal MAX_CANTIDAD = BigDecimal.valueOf(20);

    private final ConsultarEmprendimientoParaPedidoPort emprendimientos;
    private final ConsultarDisponibilidadPort productos;
    private final ConsultarMediosPagoPort mediosPago;
    private final CalcularTarifaDomicilioPort tarifas;
    private final Clock clock;

    public CotizarPedidoUseCase(
            ConsultarEmprendimientoParaPedidoPort emprendimientos,
            ConsultarDisponibilidadPort productos,
            ConsultarMediosPagoPort mediosPago,
            CalcularTarifaDomicilioPort tarifas,
            Clock clock
    ) {
        this.emprendimientos = Objects.requireNonNull(emprendimientos, "emprendimientos");
        this.productos = Objects.requireNonNull(productos, "productos");
        this.mediosPago = Objects.requireNonNull(mediosPago, "mediosPago");
        this.tarifas = Objects.requireNonNull(tarifas, "tarifas");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public Cotizacion cotizar(Solicitud solicitud) {
        validarSolicitud(solicitud);
        var emprendimiento = emprendimientos.consultar(solicitud.emprendimientoId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El emprendimiento no está disponible."));
        var mediosDelVendedor = mediosPago.consultar(emprendimiento.vendedorId());
        List<ModalidadEntrega> modalidades = modalidades(mediosDelVendedor.vendedorPuntoFijo(),
                mediosDelVendedor.domicilioDisponible());
        validarModalidad(solicitud.modalidad(), modalidades, mediosDelVendedor.vendedorPuntoFijo(),
                mediosDelVendedor.domicilioDisponible());
        validarUbicacion(solicitud);
        if (solicitud.reserva() != null && !solicitud.reserva().isAfter(clock.instant())) {
            throw invalid("reserva", "La fecha de reserva debe ser futura.");
        }

        List<UUID> ids = solicitud.items().stream().map(item -> item.productoId()).toList();
        Map<UUID, ProductoDisponible> disponibles = new HashMap<>();
        for (ProductoDisponible producto : productos.consultar(emprendimiento.id(), ids)) {
            disponibles.put(producto.productoId(), producto);
        }

        List<Item> items = new ArrayList<>();
        long subtotal = 0;
        boolean reserva = solicitud.reserva() != null;
        for (var solicitado : solicitud.items()) {
            ProductoDisponible producto = disponibles.get(solicitado.productoId());
            if (producto == null) {
                throw new DomainException("NOT_FOUND", "Uno de los productos no está disponible.");
            }
            if (!reserva) validarInventario(producto, solicitado.cantidad());
            long itemSubtotal = calcularSubtotal(producto.precio(), solicitado.cantidad());
            try {
                subtotal = Math.addExact(subtotal, itemSubtotal);
            } catch (ArithmeticException exception) {
                throw invalid("items", "El total del pedido excede el valor permitido.");
            }
            items.add(new Item(producto.productoId(), producto.nombre(), producto.unidad(),
                    solicitado.cantidad(), producto.precio(), itemSubtotal, producto.estadoProducto()));
        }

        Long tarifaDomicilio = null;
        if (solicitud.modalidad() == ModalidadEntrega.DOMICILIO) {
            GeoPoint destino = new GeoPoint(solicitud.ubicacionEntrega().latitud(),
                    solicitud.ubicacionEntrega().longitud());
            tarifaDomicilio = tarifas.calcular(emprendimiento.puntoA(), destino);
        }
        long total;
        try {
            total = Math.addExact(subtotal, tarifaDomicilio == null ? 0 : tarifaDomicilio);
        } catch (ArithmeticException exception) {
            throw invalid("items", "El total del pedido excede el valor permitido.");
        }
        List<String> pagosDisponibles = solicitud.modalidad() == ModalidadEntrega.DOMICILIO
                ? List.of("CUENTA_BANCARIA")
                : mediosDelVendedor.mediosHabilitados();
        PagoDomicilio pagoDomicilio = solicitud.modalidad() == ModalidadEntrega.DOMICILIO
                ? new PagoDomicilio("CUENTA_BANCARIA", "AL_SER_ACEPTADO", total)
                : null;
        return new Cotizacion(items, subtotal, tarifaDomicilio, total, modalidades,
                pagosDisponibles, pagoDomicilio, List.of());
    }

    private void validarSolicitud(Solicitud solicitud) {
        if (solicitud == null || solicitud.emprendimientoId() == null) {
            throw invalid("emprendimientoId", "Selecciona un emprendimiento disponible.");
        }
        if (solicitud.modalidad() == null) throw invalid("modalidad", "Selecciona una modalidad de entrega.");
        if (solicitud.items() == null || solicitud.items().isEmpty() || solicitud.items().size() > 20) {
            throw invalid("items", "El pedido debe tener entre 1 y 20 productos distintos.");
        }
        Set<UUID> ids = new HashSet<>();
        for (var item : solicitud.items()) {
            if (item == null || item.productoId() == null) {
                throw invalid("items", "Cada producto debe tener un identificador válido.");
            }
            if (!ids.add(item.productoId())) throw invalid("items", "No repitas un producto en el pedido.");
            if (item.cantidad() == null || item.cantidad().signum() <= 0
                    || item.cantidad().compareTo(MAX_CANTIDAD) > 0
                    || item.cantidad().stripTrailingZeros().scale() > 2) {
                throw invalid("items", "Cada producto debe tener una cantidad entre 0 y 20, con hasta dos decimales.");
            }
        }
    }

    private List<ModalidadEntrega> modalidades(boolean puntoFijo, boolean domicilioDisponible) {
        if (!puntoFijo) return List.of(ModalidadEntrega.ENTREGA_DIRECTA);
        return domicilioDisponible
                ? List.of(ModalidadEntrega.RECOGIDA, ModalidadEntrega.DOMICILIO)
                : List.of(ModalidadEntrega.RECOGIDA);
    }

    private void validarModalidad(
            ModalidadEntrega modalidad,
            List<ModalidadEntrega> disponibles,
            boolean puntoFijo,
            boolean domicilioDisponible
    ) {
        if (modalidad == ModalidadEntrega.DOMICILIO && puntoFijo && !domicilioDisponible) {
            throw new DomainException("BANK_ACCOUNT_REQUIRED", "Este vendedor no ofrece domicilio.");
        }
        if (!disponibles.contains(modalidad)) {
            throw new DomainException("BUSINESS_RULE", "El vendedor no ofrece esta modalidad de entrega.",
                    Map.of("motivo", "MODALIDAD_NO_DISPONIBLE"));
        }
    }

    private void validarUbicacion(Solicitud solicitud) {
        boolean requiereUbicacion = solicitud.modalidad() != ModalidadEntrega.RECOGIDA;
        var ubicacion = solicitud.ubicacionEntrega();
        if (requiereUbicacion != (ubicacion != null)) {
            throw invalid("ubicacionEntrega", requiereUbicacion
                    ? "Indica la ubicación de entrega." : "No envíes una ubicación para recoger en el punto fijo.");
        }
        if (ubicacion != null && (ubicacion.direccion() == null || ubicacion.direccion().isBlank()
                || ubicacion.direccion().length() > 300
                || ubicacion.referencia() != null && ubicacion.referencia().length() > 300)) {
            throw invalid("ubicacionEntrega", "Escribe una dirección válida de hasta 300 caracteres.");
        }
        if (ubicacion != null) new GeoPoint(ubicacion.latitud(), ubicacion.longitud());
    }

    private void validarInventario(ProductoDisponible producto, BigDecimal cantidad) {
        if ("AGOTADO".equals(producto.estadoProducto())) {
            throw new DomainException("PRODUCT_UNAVAILABLE", "El producto agotado solo se acepta en una reserva.",
                    Map.of("productoId", producto.productoId()));
        }
        if (cantidad.compareTo(producto.cantidadDisponible()) > 0) {
            throw new DomainException("OUT_OF_STOCK", "La cantidad solicitada supera la disponibilidad actual.",
                    Map.of("productoId", producto.productoId(), "disponible", producto.cantidadDisponible()));
        }
    }

    private long calcularSubtotal(long precio, BigDecimal cantidad) {
        try {
            return BigDecimal.valueOf(precio).multiply(cantidad)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException exception) {
            throw invalid("items", "El subtotal del producto excede el valor permitido.");
        }
    }

    private DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
