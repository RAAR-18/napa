package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ListarProductosPort;
import co.edu.napa.emprendimientos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class ListarProductosUseCase implements ListarProductosPort {
    private final ProductoRepositoryPort productos;

    public ListarProductosUseCase(ProductoRepositoryPort productos) {
        this.productos = Objects.requireNonNull(productos, "productos");
    }

    @Override
    public Resultado listar(int page, int size) {
        return convertir(pagina(page, size), productos.findDisponibles(PageRequest.of(page, size)));
    }

    @Override
    public Resultado listarDelVendedor(UUID usuarioId, int page, int size) {
        return convertir(pagina(page, size), productos.findDisponiblesByUsuario(usuarioId, PageRequest.of(page, size)));
    }

    private int pagina(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException("VALIDATION_ERROR", "La paginación debe usar page >= 0 y size entre 1 y 100.");
        }
        return page;
    }

    private Resultado convertir(int page, Page<ProductoRepositoryPort.ProductoListRow> result) {
        return new Resultado(
                result.getContent().stream()
                        .map(row -> new Item(
                                row.getId(), row.getEmprendimientoId(), row.getEmprendimientoNombre(),
                                row.getNombre(), row.getPrecio(), row.getUnidad(), row.getDescripcion(),
                                row.getCantidadDisponible(), row.getImagenUrl()
                        ))
                        .toList(),
                page, result.getSize(), result.getTotalElements()
        );
    }
}
