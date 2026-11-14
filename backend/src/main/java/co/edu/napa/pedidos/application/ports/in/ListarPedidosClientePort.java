package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.application.ports.out.FiltroPedidosVendedor;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-47: el cliente lista los pedidos que hizo. */
public interface ListarPedidosClientePort {
    Pagina listar(UUID clienteId, FiltroPedidosVendedor filtro, int page, int size);

    record Pagina(List<Item> content, int page, int size, long totalElements) {
        public Pagina {
            content = List.copyOf(content);
        }
    }

    record Item(
            UUID id,
            Emprendimiento emprendimiento,
            ModalidadEntrega modalidad,
            Instant createdAt,
            EstadoPedido estado,
            Instant reserva,
            long total
    ) {}

    record Emprendimiento(UUID id, String nombre) {}
}
