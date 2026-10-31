package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.emprendimientos.domain.model.EstadoProducto;
import co.edu.napa.emprendimientos.domain.model.Producto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "producto")
public class ProductoJpaEntity {
    @Id
    private UUID id;
    @Column(name = "emprendimiento_id", nullable = false)
    private UUID emprendimientoId;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;
    @Column(nullable = false, length = 40)
    private String unidad;
    @Column(length = 500)
    private String descripcion;
    @Column(name = "cantidad_disponible", nullable = false)
    private int cantidadDisponible;
    @Column(name = "imagen_url", columnDefinition = "TEXT")
    private String imagenUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoProducto estado;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductoJpaEntity() {}

    public ProductoJpaEntity(Producto producto) {
        id = producto.id();
        emprendimientoId = producto.emprendimientoId();
        nombre = producto.nombre();
        precio = producto.precio();
        unidad = producto.unidad();
        descripcion = producto.descripcion();
        cantidadDisponible = producto.cantidadDisponible();
        imagenUrl = producto.imagenUrl();
        estado = producto.estado();
        createdAt = producto.createdAt();
        updatedAt = producto.updatedAt();
    }

    public Producto toDomain() {
        return new Producto(id, emprendimientoId, nombre, precio, unidad, descripcion,
                cantidadDisponible, imagenUrl, estado, createdAt, updatedAt);
    }
}
