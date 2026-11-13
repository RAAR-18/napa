package co.edu.napa.pedidos.application.ports.in;

import java.util.UUID;

/** RF-27: quién lleva el pedido, para el cliente y el vendedor de punto fijo del pedido. */
public interface ConsultarDomiciliarioPedidoPort {
    DomiciliarioDePedido consultarComoCliente(UUID clienteId, UUID pedidoId);

    DomiciliarioDePedido consultarComoVendedor(UUID vendedorId, UUID pedidoId);

    /** {@code domiciliario} es nulo si el pedido no tiene domicilio o nadie lo ha aceptado todavía. */
    record DomiciliarioDePedido(UUID pedidoId, UUID domicilioId, String estadoDomicilio, Domiciliario domiciliario) {}

    /** Sin celular ni documento (FR-003). La calificación es nula hasta que exista RF-01. */
    record Domiciliario(String nombre, String fotoUrl, Calificacion calificacion) {}

    record Calificacion(double promedio, long total) {}
}
