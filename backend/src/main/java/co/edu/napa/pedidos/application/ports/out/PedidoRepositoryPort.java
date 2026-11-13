package co.edu.napa.pedidos.application.ports.out;

import co.edu.napa.pedidos.domain.model.Pedido;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepositoryPort {
    void guardar(Pedido pedido);
    Optional<Pedido> bloquearPorId(UUID pedidoId);
    int contarItems(UUID pedidoId);
    /** Pedidos sin cerrar del emprendimiento: pendientes, aceptados, listos o en camino (RF-29, RF-30). */
    long contarSinCerrarDeEmprendimiento(UUID emprendimientoId);
    void actualizar(Pedido pedido);
    List<PedidoListadoProjection> listarPedidosVendedor(
            UUID vendedorId,
            FiltroPedidosVendedor filtro,
            long offset,
            int limit
    );
    long contarPedidosVendedor(UUID vendedorId, FiltroPedidosVendedor filtro);
    Optional<PedidoDetalleProjection> consultarDetalleVendedor(UUID vendedorId, UUID pedidoId);

    /** Pedidos hechos por el cliente (RF-47); los filtros son los mismos del vendedor. */
    List<PedidoListadoProjection> listarPedidosCliente(
            UUID clienteId,
            FiltroPedidosVendedor filtro,
            long offset,
            int limit
    );

    long contarPedidosCliente(UUID clienteId, FiltroPedidosVendedor filtro);

    /** Detalle de un pedido del cliente (RF-48); vacío si no existe o es de otro. */
    Optional<PedidoDetalleProjection> consultarDetalleCliente(UUID clienteId, UUID pedidoId);
}
