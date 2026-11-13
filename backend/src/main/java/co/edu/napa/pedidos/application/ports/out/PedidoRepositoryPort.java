package co.edu.napa.pedidos.application.ports.out;

import co.edu.napa.pedidos.domain.model.Pedido;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepositoryPort {
    void guardar(Pedido pedido);
    Optional<Pedido> bloquearPorId(UUID pedidoId);
    void actualizar(Pedido pedido);
    List<PedidoListadoProjection> listarPedidosVendedor(
            UUID vendedorId,
            FiltroPedidosVendedor filtro,
            long offset,
            int limit
    );
    long contarPedidosVendedor(UUID vendedorId, FiltroPedidosVendedor filtro);
    Optional<PedidoDetalleProjection> consultarDetalleVendedor(UUID vendedorId, UUID pedidoId);
}
