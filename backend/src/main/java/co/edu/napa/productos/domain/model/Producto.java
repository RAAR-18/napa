package co.edu.napa.productos.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Producto(UUID id, UUID emprendimientoId, String nombre, long precio, UnidadProducto unidad,
                       String descripcion, BigDecimal cantidadDisponible, EstadoProducto estado,
                       Instant createdAt, Instant updatedAt, long version) {
    public Producto {
        Objects.requireNonNull(id);
        Objects.requireNonNull(emprendimientoId);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (version < 0) throw new IllegalArgumentException("La versión no puede ser negativa.");
        nombre = nombre == null ? null : nombre.strip();
        descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.strip();
        if (nombre == null || nombre.isEmpty() || nombre.length() > 100) {
            throw invalid("nombre", "Escribe un nombre de hasta 100 caracteres.");
        }
        if (precio <= 0 || precio > 9_007_199_254_740_991L) {
            throw invalid("precio", "El precio debe ser un entero positivo en pesos.");
        }
        if (unidad == null) throw invalid("unidad", "Selecciona una unidad válida.");
        if (descripcion != null && descripcion.length() > 500) {
            throw invalid("descripcion", "La descripción admite hasta 500 caracteres.");
        }
        validateQuantity(cantidadDisponible);
        if (estado != EstadoProducto.ELIMINADO
                && (cantidadDisponible.signum() == 0) != (estado == EstadoProducto.AGOTADO)) {
            throw invalid("cantidadDisponible", "La cantidad y la disponibilidad no coinciden.");
        }
    }

    public static Producto crear(UUID emprendimientoId, String nombre, BigDecimal precio, UnidadProducto unidad,
                                 String descripcion, BigDecimal cantidad, Instant now) {
        validateQuantity(cantidad);
        if (cantidad.signum() == 0) {
            throw invalid("cantidadDisponible", "La cantidad inicial debe ser mayor que cero.");
        }
        return new Producto(UUID.randomUUID(), emprendimientoId, nombre, pesos(precio), unidad, descripcion,
                cantidad, EstadoProducto.DISPONIBLE, now, now, 0);
    }

    private static long pesos(BigDecimal precio) {
        try {
            return precio.longValueExact();
        } catch (ArithmeticException | NullPointerException exception) {
            throw invalid("precio", "El precio debe ser un entero positivo en pesos.");
        }
    }

    public Producto conInventario(BigDecimal cantidad, Instant now) {
        requirePublished();
        validateQuantity(cantidad);
        return new Producto(id, emprendimientoId, nombre, precio, unidad, descripcion, cantidad,
                cantidad.signum() == 0 ? EstadoProducto.AGOTADO : EstadoProducto.DISPONIBLE, createdAt, now, version);
    }

    public Producto editar(String nuevoNombre, BigDecimal nuevoPrecio, boolean cambiarDescripcion,
                           String nuevaDescripcion, Instant now) {
        requirePublished();
        return new Producto(id, emprendimientoId, nuevoNombre == null ? nombre : nuevoNombre,
                nuevoPrecio == null ? precio : pesos(nuevoPrecio), unidad,
                cambiarDescripcion ? nuevaDescripcion : descripcion, cantidadDisponible, estado, createdAt, now, version);
    }

    public Producto eliminar(Instant now) {
        requirePublished();
        return new Producto(id, emprendimientoId, nombre, precio, unidad, descripcion, cantidadDisponible,
                EstadoProducto.ELIMINADO, createdAt, now, version);
    }

    public void requirePublished() {
        if (estado == EstadoProducto.ELIMINADO) {
            throw new DomainException("INVALID_STATE", "Este producto fue eliminado.",
                    Map.of("estadoActual", estado.name()));
        }
    }

    private static void validateQuantity(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("99999999.99")) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw invalid("cantidadDisponible", "Usa una cantidad entre 0 y 99999999,99, con hasta dos decimales.");
        }
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
