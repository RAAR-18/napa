package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.pedidos.application.ports.in.ListarPedidosClientePort;
import co.edu.napa.pedidos.application.ports.out.FiltroPedidosVendedor;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class ListarPedidosClienteUseCase implements ListarPedidosClientePort {
    private static final int MAX_PAGE_SIZE = 100;

    private final PedidoRepositoryPort pedidos;

    public ListarPedidosClienteUseCase(PedidoRepositoryPort pedidos) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina listar(UUID clienteId, FiltroPedidosVendedor filtro, int page, int size) {
        if (clienteId == null || filtro == null) {
            throw validationError("query", "La identidad del cliente y los filtros son obligatorios.");
        }
        if (page < 0) {
            throw validationError("page", "La página debe ser mayor o igual a cero.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw validationError("size", "El tamaño debe estar entre 1 y 100.");
        }
        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw validationError("desde", "La fecha inicial no puede ser posterior a la fecha final.");
        }
        long offset = Math.multiplyExact((long) page, size);
        var content = pedidos.listarPedidosCliente(clienteId, filtro, offset, size).stream()
                .map(row -> new Item(
                        row.id(),
                        new Emprendimiento(row.emprendimientoId(), row.emprendimientoNombre()),
                        row.modalidad(),
                        row.createdAt(),
                        row.estado(),
                        row.reserva(),
                        row.total()))
                .toList();
        return new Pagina(content, page, size, pedidos.contarPedidosCliente(clienteId, filtro));
    }

    private DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
