package co.edu.napa.pedidos.application.ports.in;

import java.util.List;

public record PedidosVendedorPagina(
        List<PedidoVendedorListadoItem> content,
        int page,
        int size,
        long totalElements
) {
    public PedidosVendedorPagina {
        content = List.copyOf(content);
    }
}
