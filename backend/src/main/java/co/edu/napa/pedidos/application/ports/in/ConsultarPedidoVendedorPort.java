package co.edu.napa.pedidos.application.ports.in;

import java.util.UUID;

public interface ConsultarPedidoVendedorPort {
    PedidoVendedorDetalle consultar(UUID vendedorId, UUID pedidoId);
}
