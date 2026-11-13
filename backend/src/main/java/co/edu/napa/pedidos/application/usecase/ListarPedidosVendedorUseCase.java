package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.pedidos.application.ports.in.ListarPedidosVendedorPort;
import co.edu.napa.pedidos.application.ports.in.PedidoVendedorListadoItem;
import co.edu.napa.pedidos.application.ports.in.PedidosVendedorPagina;
import co.edu.napa.pedidos.application.ports.out.FiltroPedidosVendedor;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class ListarPedidosVendedorUseCase implements ListarPedidosVendedorPort {
    private static final int MAX_PAGE_SIZE = 100;

    private final PedidoRepositoryPort pedidos;
    private final ConsultarNombresUsuarioPort usuarios;

    public ListarPedidosVendedorUseCase(
            PedidoRepositoryPort pedidos,
            ConsultarNombresUsuarioPort usuarios
    ) {
        this.pedidos = Objects.requireNonNull(pedidos, "pedidos");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    public PedidosVendedorPagina listar(UUID vendedorId, FiltroPedidosVendedor filtro, int page, int size) {
        if (vendedorId == null || filtro == null) {
            throw validationError("query", "La identidad del vendedor y los filtros son obligatorios.");
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
        var rows = pedidos.listarPedidosVendedor(vendedorId, filtro, offset, size);
        Map<UUID, String> names = usuarios.consultar(rows.stream()
                .map(item -> item.clienteId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        List<PedidoVendedorListadoItem> content = rows.stream()
                .map(item -> new PedidoVendedorListadoItem(
                        item.id(),
                        new PedidoVendedorListadoItem.Cliente(requireName(names, item.clienteId())),
                        item.createdAt(),
                        item.estado(),
                        item.modalidad(),
                        item.reserva(),
                        item.total()))
                .toList();
        return new PedidosVendedorPagina(
                content,
                page,
                size,
                pedidos.contarPedidosVendedor(vendedorId, filtro)
        );
    }

    private String requireName(Map<UUID, String> names, UUID userId) {
        String name = names.get(userId);
        if (name == null) {
            throw new DomainException("NOT_FOUND", "La información del cliente no está disponible.");
        }
        return name;
    }

    private DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
