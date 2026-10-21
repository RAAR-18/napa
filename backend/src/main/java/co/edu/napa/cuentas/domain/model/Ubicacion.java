package co.edu.napa.cuentas.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Ubicacion(
        UUID id,
        double latitud,
        double longitud,
        String direccion,
        String referencia,
        Instant createdAt,
        Instant updatedAt
) {
    public Ubicacion {
        Objects.requireNonNull(id, "id");
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new IllegalArgumentException("latitud must be between -90 and 90");
        }
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("longitud must be between -180 and 180");
        }
        if (direccion == null || direccion.isBlank() || direccion.length() > 300) {
            throw new IllegalArgumentException("direccion must contain between 1 and 300 characters");
        }
        if (referencia != null && referencia.length() > 300) {
            throw new IllegalArgumentException("referencia cannot exceed 300 characters");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
