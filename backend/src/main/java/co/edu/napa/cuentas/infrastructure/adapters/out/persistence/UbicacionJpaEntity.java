package co.edu.napa.cuentas.infrastructure.persistence;

import co.edu.napa.cuentas.domain.model.Ubicacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ubicacion")
public class UbicacionJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private double latitud;

    @Column(nullable = false)
    private double longitud;

    @Column(nullable = false, length = 300)
    private String direccion;

    @Column(length = 300)
    private String referencia;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UbicacionJpaEntity() {
    }

    public UbicacionJpaEntity(Ubicacion ubicacion) {
        id = ubicacion.id();
        latitud = ubicacion.latitud();
        longitud = ubicacion.longitud();
        direccion = ubicacion.direccion();
        referencia = ubicacion.referencia();
        createdAt = ubicacion.createdAt();
        updatedAt = ubicacion.updatedAt();
    }

    public Ubicacion toDomain() {
        return new Ubicacion(id, latitud, longitud, direccion, referencia, createdAt, updatedAt);
    }
}
