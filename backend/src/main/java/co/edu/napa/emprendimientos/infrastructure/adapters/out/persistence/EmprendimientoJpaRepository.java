package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface EmprendimientoJpaRepository extends JpaRepository<EmprendimientoJpaEntity, UUID> {
    Optional<EmprendimientoJpaEntity> findByUsuarioIdAndEstado(
            UUID usuarioId,
            co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento estado
    );

    @Query(value = """
            SELECT e.id AS id, e.nombre AS nombre, e.categoria AS categoria,
                   u.rol AS tipoVendedor, e.descripcion AS descripcion,
                   e.imagen_url AS imagenUrl,
                   e.hora_apertura AS horaApertura, e.hora_cierre AS horaCierre,
                   ub.latitud AS latitud, ub.longitud AS longitud,
                   ub.direccion AS direccion, ub.referencia AS referencia
            FROM emprendimiento e
            JOIN usuario u ON u.id = e.usuario_id
            JOIN ubicacion ub ON ub.id = e.ubicacion_id
            WHERE e.estado = 'ACTIVO'
              AND u.estado = 'ACTIVA'
              AND u.rol IN ('VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO')
            ORDER BY e.nombre ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM emprendimiento e
                    JOIN usuario u ON u.id = e.usuario_id
                    WHERE e.estado = 'ACTIVO'
                      AND u.estado = 'ACTIVA'
                      AND u.rol IN ('VENDEDOR_AMBULANTE', 'VENDEDOR_PUNTO_FIJO')
                    """,
            nativeQuery = true)
    Page<co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort.EmprendimientoListRow>
    findActivos(Pageable pageable);
}
