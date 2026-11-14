package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CotizarPedidoPort {
    Cotizacion cotizar(Solicitud solicitud);

    record Solicitud(
            UUID emprendimientoId,
            ModalidadEntrega modalidad,
            List<ItemSolicitud> items,
            UbicacionEntrega ubicacionEntrega,
            Instant reserva
    ) {}

    record ItemSolicitud(UUID productoId, BigDecimal cantidad) {}

    record UbicacionEntrega(
            double latitud,
            double longitud,
            String direccion,
            String referencia,
            String barrio
    ) {
        public UbicacionEntrega(double latitud, double longitud, String direccion, String referencia) {
            this(latitud, longitud, direccion, referencia, null);
        }
    }

    record Cotizacion(
            List<Item> items,
            long subtotal,
            Long tarifaDomicilio,
            long total,
            List<ModalidadEntrega> modalidadesDisponibles,
            List<String> mediosPagoDisponibles,
            PagoDomicilio pagoDomicilio,
            List<String> advertencias
    ) {
        public Cotizacion {
            items = List.copyOf(items);
            modalidadesDisponibles = List.copyOf(modalidadesDisponibles);
            mediosPagoDisponibles = List.copyOf(mediosPagoDisponibles);
            advertencias = List.copyOf(advertencias);
        }
    }

    record Item(
            UUID productoId,
            String nombre,
            String unidad,
            BigDecimal cantidad,
            long precioUnitario,
            long subtotal,
            String estadoProducto
    ) {}

    record PagoDomicilio(String medio, String momento, long total) {}
}
