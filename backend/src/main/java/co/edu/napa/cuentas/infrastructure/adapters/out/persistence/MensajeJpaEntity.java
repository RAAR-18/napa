package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoMensaje;
import co.edu.napa.cuentas.domain.model.Mensaje;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensaje")
public class MensajeJpaEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CanalVerificacion canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PropositoVerificacion proposito;

    @Column(nullable = false, length = 16)
    private String destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoMensaje estado;

    @Column(name = "proveedor_id", length = 64)
    private String proveedorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MensajeJpaEntity() {
    }

    public MensajeJpaEntity(Mensaje mensaje) {
        id = mensaje.id();
        canal = mensaje.canal();
        proposito = mensaje.proposito();
        destino = mensaje.destino();
        estado = mensaje.estado();
        proveedorId = mensaje.proveedorId();
        createdAt = mensaje.createdAt();
        updatedAt = mensaje.updatedAt();
    }

    public Mensaje toDomain() {
        return new Mensaje(id, canal, proposito, destino, estado, proveedorId, createdAt, updatedAt);
    }
}