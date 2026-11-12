package co.edu.napa.pedidos.application.ports.out;

import co.edu.napa.pedidos.domain.model.Pedido;

public interface PedidoRepositoryPort {
    void guardar(Pedido pedido);
}
