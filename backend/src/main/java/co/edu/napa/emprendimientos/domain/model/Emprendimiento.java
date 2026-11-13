package co.edu.napa.emprendimientos.domain.model;

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
        LocalTime horaApertura,
        LocalTime horaCierre,
        EstadoEmprendimiento estado,
        Instant createdAt,
        Instant updatedAt
) {
    public Emprendimiento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(ubicacionId, "ubicacionId");
        validateText(nombre, "nombre", 100);
        if (categoria == null) {
            throw validationError("categoria", "La categoría es obligatoria.");
        }
        validateText(descripcion, "descripcion", 500);
        if (horaApertura == null) {
            throw validationError("horaApertura", "La hora de apertura es obligatoria.");
        }
        if (horaCierre == null) {
            throw validationError("horaCierre", "La hora de cierre es obligatoria.");
        }
        if (horaApertura.equals(horaCierre)) {
            throw validationError("horaCierre", "La hora de apertura y cierre deben ser diferentes.");
        }
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static void validateText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw validationError(field, "El campo " + field + " es obligatorio y no debe exceder " + maxLength + " caracteres.");
        }
    }

    private static DomainException validationError(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, java.util.Map.of(
                "fields",
                java.util.Map.of(field, message)
        ));
    }
}
