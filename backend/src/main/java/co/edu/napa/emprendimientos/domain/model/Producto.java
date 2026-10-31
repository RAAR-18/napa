package co.edu.napa.emprendimientos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Producto(
        UUID id,
        UUID emprendimientoId,
        String nombre,
        BigDecimal precio,
        String unidad,
        String descripcion,
        int cantidadDisponible,
        String imagenUrl,
        EstadoProducto estado,
        Instant createdAt,
        Instant updatedAt
) {
    public Producto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(emprendimientoId, "emprendimientoId");
        Objects.requireNonNull(precio, "precio");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Producto crear(
            UUID emprendimientoId,
            String nombre,
            BigDecimal precio,
            String unidad,
            String descripcion,
            int cantidadDisponible,
            String imagenUrl,
            Instant now
    ) {
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 120) {
            throw new DomainException("VALIDATION_ERROR", "El nombre del producto es obligatorio y no puede superar 120 caracteres.");
        }
        if (precio == null || precio.signum() <= 0) {
            throw new DomainException("VALIDATION_ERROR", "El precio debe ser mayor que cero.");
        }
        if (unidad == null || unidad.isBlank() || unidad.trim().length() > 40) {
            throw new DomainException("VALIDATION_ERROR", "La unidad es obligatoria y no puede superar 40 caracteres.");
        }
        if (cantidadDisponible <= 0) {
            throw new DomainException("VALIDATION_ERROR", "La cantidad disponible debe ser mayor que cero.");
        }
        if (imagenUrl != null && imagenUrl.length() > 2_000_000) {
            throw new DomainException("VALIDATION_ERROR", "La imagen no puede superar 2 MB.");
        }
        return new Producto(
                UUID.randomUUID(),
                Objects.requireNonNull(emprendimientoId, "emprendimientoId"),
                nombre.trim(),
                precio,
                unidad.trim(),
                descripcion == null || descripcion.isBlank() ? null : descripcion.trim(),
                cantidadDisponible,
                imagenUrl == null || imagenUrl.isBlank() ? null : imagenUrl,
                EstadoProducto.DISPONIBLE,
                now,
                now
        );
    }
}
