package co.edu.napa.emprendimientos.domain.model;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.shared.domain.DomainException;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public record Emprendimiento(
        UUID id,
        UUID usuarioId,
        UUID ubicacionId,
        String nombre,
        CategoriaEmprendimiento categoria,
        String descripcion,
        String imagenUrl,
        LocalTime horaApertura,
        LocalTime horaCierre,
        EstadoEmprendimiento estado,
        RolUsuario tipoVendedor,
        Instant createdAt,
        Instant updatedAt
) {
    public Emprendimiento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(ubicacionId, "ubicacionId");
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(horaApertura, "horaApertura");
        Objects.requireNonNull(horaCierre, "horaCierre");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(tipoVendedor, "tipoVendedor");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Emprendimiento crear(
            UUID usuarioId,
            RolUsuario tipoVendedor,
            UUID ubicacionId,
            String nombre,
            CategoriaEmprendimiento categoria,
            String descripcion,
            String imagenUrl,
            LocalTime horaApertura,
            LocalTime horaCierre,
            Instant now
    ) {
        if (tipoVendedor != RolUsuario.VENDEDOR_AMBULANTE
                && tipoVendedor != RolUsuario.VENDEDOR_PUNTO_FIJO) {
            throw new DomainException("FORBIDDEN", "Solo los vendedores pueden crear un emprendimiento.");
        }
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 120) {
            throw new DomainException("VALIDATION_ERROR", "El nombre es obligatorio y no puede superar 120 caracteres.");
        }
        if (categoria == null) {
            throw new DomainException("VALIDATION_ERROR", "La categoría es obligatoria.");
        }
        if (horaApertura == null || horaCierre == null || !horaCierre.isAfter(horaApertura)) {
            throw new DomainException("VALIDATION_ERROR", "La hora de cierre debe ser posterior a la hora de apertura.");
        }
        return new Emprendimiento(
                UUID.randomUUID(),
                usuarioId,
                Objects.requireNonNull(ubicacionId, "ubicacionId"),
                nombre.trim(),
                categoria,
                descripcion == null || descripcion.isBlank() ? null : descripcion.trim(),
                imagenUrl == null || imagenUrl.isBlank() ? null : imagenUrl,
                horaApertura,
                horaCierre,
                EstadoEmprendimiento.ACTIVO,
                tipoVendedor,
                now,
                now
        );
    }
}
