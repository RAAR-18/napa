package co.edu.napa.pedidos.application.ports.in;

import java.util.UUID;

/** RF-52: el vendedor ve a quién le vende, solo con lo necesario para gestionar el pedido. */
public interface ConsultarClientePedidoPort {
    ClientePedido consultar(UUID vendedorId, UUID pedidoId);

    /** {@code celular} y {@code direccionEntrega} son nulos con el pedido cerrado, de recogida o la cuenta eliminada. */
    record ClientePedido(String nombre, String celular, DireccionEntrega direccionEntrega) {}

    record DireccionEntrega(double latitud, double longitud, String direccion, String referencia) {}
}
