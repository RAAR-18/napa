package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.emprendimientos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.Producto;
import co.edu.napa.shared.domain.DomainException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProductoPersistenceAdapter implements ProductoRepositoryPort {
    private final ProductoJpaRepository repository;

    public ProductoPersistenceAdapter(ProductoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Producto save(Producto producto) {
        try {
            return repository.saveAndFlush(new ProductoJpaEntity(producto)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && "uq_producto_nombre_emprendimiento".equals(violation.getConstraintName())) {
                throw new DomainException("CONFLICT", "Ya existe un producto con ese nombre en tu emprendimiento.");
            }
            throw exception;
        }
    }

    @Override
    public Page<ProductoListRow> findDisponibles(Pageable pageable) {
        return repository.findDisponibles(pageable);
    }

    @Override
    public Page<ProductoListRow> findDisponiblesByUsuario(UUID usuarioId, Pageable pageable) {
        return repository.findDisponiblesByUsuario(usuarioId, pageable);
    }
}
