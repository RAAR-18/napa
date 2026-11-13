package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.application.ports.out.FiltroPedidosVendedor;

import java.util.UUID;

public interface ListarPedidosVendedorPort {
    PedidosVendedorPagina listar(UUID vendedorId, FiltroPedidosVendedor filtro, int page, int size);
}
