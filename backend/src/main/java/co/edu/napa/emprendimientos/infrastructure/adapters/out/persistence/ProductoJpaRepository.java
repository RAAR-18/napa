package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.emprendimientos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.EstadoProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductoJpaRepository extends JpaRepository<ProductoJpaEntity, UUID> {
    @Query(value = """
            SELECT p.id AS id, p.emprendimiento_id AS emprendimientoId,
                   e.nombre AS emprendimientoNombre, p.nombre AS nombre,
                   p.precio AS precio, p.unidad AS unidad, p.descripcion AS descripcion,
                   p.cantidad_disponible AS cantidadDisponible, p.imagen_url AS imagenUrl
            FROM producto p
            JOIN emprendimiento e ON e.id = p.emprendimiento_id
            JOIN usuario u ON u.id = e.usuario_id
            WHERE p.estado = 'DISPONIBLE' AND e.estado = 'ACTIVO' AND u.estado = 'ACTIVA'
            ORDER BY e.nombre ASC, p.nombre ASC
            """, countQuery = """
            SELECT COUNT(*) FROM producto p
            JOIN emprendimiento e ON e.id = p.emprendimiento_id
            JOIN usuario u ON u.id = e.usuario_id
            WHERE p.estado = 'DISPONIBLE' AND e.estado = 'ACTIVO' AND u.estado = 'ACTIVA'
            """, nativeQuery = true)
    Page<ProductoRepositoryPort.ProductoListRow> findDisponibles(Pageable pageable);

    @Query(value = """
            SELECT p.id AS id, p.emprendimiento_id AS emprendimientoId,
                   e.nombre AS emprendimientoNombre, p.nombre AS nombre,
                   p.precio AS precio, p.unidad AS unidad, p.descripcion AS descripcion,
                   p.cantidad_disponible AS cantidadDisponible, p.imagen_url AS imagenUrl
            FROM producto p
            JOIN emprendimiento e ON e.id = p.emprendimiento_id
            WHERE p.estado <> 'INACTIVO' AND e.usuario_id = :usuarioId
            ORDER BY p.nombre ASC
            """, countQuery = """
            SELECT COUNT(*) FROM producto p
            JOIN emprendimiento e ON e.id = p.emprendimiento_id
            WHERE p.estado <> 'INACTIVO' AND e.usuario_id = :usuarioId
            """, nativeQuery = true)
    Page<ProductoRepositoryPort.ProductoListRow> findDisponiblesByUsuario(@Param("usuarioId") UUID usuarioId, Pageable pageable);
}
