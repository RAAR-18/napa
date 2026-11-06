package co.edu.napa.emprendimientos.infrastructure.adapters.out.persistence;

import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "emprendimiento")
public class EmprendimientoJpaEntity {
    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "ubicacion_id", nullable = false, unique = true)
    private UUID ubicacionId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CategoriaEmprendimiento categoria;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoEmprendimiento estado;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmprendimientoJpaEntity() {
    }

    public EmprendimientoJpaEntity(Emprendimiento emprendimiento) {
        id = emprendimiento.id();
        usuarioId = emprendimiento.usuarioId();
        ubicacionId = emprendimiento.ubicacionId();
        nombre = emprendimiento.nombre();
        categoria = emprendimiento.categoria();
        descripcion = emprendimiento.descripcion();
        horaApertura = emprendimiento.horaApertura();
        horaCierre = emprendimiento.horaCierre();
        estado = emprendimiento.estado();
        createdAt = emprendimiento.createdAt();
        updatedAt = emprendimiento.updatedAt();
    }

    public Emprendimiento toDomain() {
        return new Emprendimiento(
                id,
                usuarioId,
                ubicacionId,
                nombre,
                categoria,
                descripcion,
                horaApertura,
                horaCierre,
                estado,
                createdAt,
                updatedAt
        );
    }
}
