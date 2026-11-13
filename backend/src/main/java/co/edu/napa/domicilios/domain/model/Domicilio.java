package co.edu.napa.domicilios.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de llevar un pedido del punto A (copia del punto fijo) al punto B (ubicación de entrega del pedido).
 * No es editable: solo cambia de estado (RF-14 FR-006).
 */
public record Domicilio(
        UUID id,
        UUID pedidoId,
        UUID vendedorId,
        UUID compradorId,
        UUID domiciliarioId,
        EstadoDomicilio estado,
        long tarifa,
        UUID ubicacionOrigenId,
        UUID ubicacionDestinoId,
        String codigoConfirmacion,
        Instant asignadoAt,
        MotivoCancelacion motivoCancelacion,
        String detalleCancelacion,
        Instant avisoSinAsignarAt,
        Instant createdAt,
        Instant updatedAt
) {
    /** Estados en los que el domicilio está en manos de un domiciliario (RF-19 FR-006). */
    public static final Set<EstadoDomicilio> EN_CURSO =
            EnumSet.of(EstadoDomicilio.ASIGNADO, EstadoDomicilio.RECOGIDO, EstadoDomicilio.EN_CAMINO);
    /** Máximo de domicilios en curso por domiciliario (RF-19 FR-006). */
    public static final int MAXIMO_EN_CURSO = 3;
    /** Longitud del motivo que escribe el administrador al cancelar (RF-13). */
    public static final int MOTIVO_MINIMO = 10;
    public static final int MOTIVO_MAXIMO = 300;

    public Domicilio {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pedidoId, "pedidoId");
        Objects.requireNonNull(vendedorId, "vendedorId");
        Objects.requireNonNull(compradorId, "compradorId");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(ubicacionOrigenId, "ubicacionOrigenId");
        Objects.requireNonNull(ubicacionDestinoId, "ubicacionDestinoId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (tarifa <= 0) {
            throw new DomainException("VALIDATION_ERROR", "La tarifa del domicilio debe ser mayor que cero.");
        }
        if (estado == EstadoDomicilio.DISPONIBLE && domiciliarioId != null) {
            throw new DomainException("VALIDATION_ERROR", "Un domicilio disponible no tiene domiciliario.");
        }
        if (codigoConfirmacion != null && !codigoConfirmacion.matches("\\d{6}")) {
            throw new DomainException("VALIDATION_ERROR", "El código de confirmación debe tener 6 dígitos.");
        }
        if (EN_CURSO.contains(estado) || estado == EstadoDomicilio.ENTREGADO) {
            if (domiciliarioId == null || codigoConfirmacion == null || asignadoAt == null) {
                throw new DomainException("VALIDATION_ERROR", "Un domicilio asignado necesita domiciliario y código.");
            }
        }
        if ((estado == EstadoDomicilio.CANCELADO) != (motivoCancelacion != null)) {
            throw new DomainException("VALIDATION_ERROR", "Solo un domicilio cancelado tiene motivo de cancelación.");
        }
        if ((motivoCancelacion == MotivoCancelacion.ADMINISTRADOR) != (detalleCancelacion != null)) {
            throw new DomainException("VALIDATION_ERROR", "Solo la cancelación del administrador lleva un motivo escrito.");
        }
    }

    public static Domicilio publicar(
            UUID pedidoId,
            UUID vendedorId,
            UUID compradorId,
            long tarifa,
            UUID ubicacionOrigenId,
            UUID ubicacionDestinoId,
            Instant now
    ) {
        return new Domicilio(UUID.randomUUID(), pedidoId, vendedorId, compradorId, null,
                EstadoDomicilio.DISPONIBLE, tarifa, ubicacionOrigenId, ubicacionDestinoId, null, null, null, null,
                null, now, now);
    }

    /** RF-19: un domiciliario toma el domicilio con la tarifa publicada; nace el código de confirmación. */
    public Domicilio asignar(UUID domiciliario, String codigo, Instant now) {
        Objects.requireNonNull(domiciliario, "domiciliario");
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(now, "now");
        requireDisponible();
        return new Domicilio(id, pedidoId, vendedorId, compradorId, domiciliario, EstadoDomicilio.ASIGNADO, tarifa,
                ubicacionOrigenId, ubicacionDestinoId, codigo, now, null, null, avisoSinAsignarAt, createdAt, now);
    }

    /** Marca que ya se avisó al vendedor y al cliente que nadie lo ha tomado (se avisa una sola vez). */
    public Domicilio marcarAvisoSinAsignar(Instant now) {
        requireDisponible();
        if (avisoSinAsignarAt != null) {
            throw new DomainException("INVALID_STATE", "Ya se avisó que el domicilio no tiene domiciliario.");
        }
        return new Domicilio(id, pedidoId, vendedorId, compradorId, domiciliarioId, estado, tarifa,
                ubicacionOrigenId, ubicacionDestinoId, codigoConfirmacion, asignadoAt, motivoCancelacion,
                detalleCancelacion, Objects.requireNonNull(now, "now"), createdAt, updatedAt);
    }

    /** Cancelación automática por falta de domiciliario (RF-14 FR-009). */
    public Domicilio cancelarPorFaltaDeAsignacion(Instant now) {
        requireDisponible();
        return new Domicilio(id, pedidoId, vendedorId, compradorId, null, EstadoDomicilio.CANCELADO, tarifa,
                ubicacionOrigenId, ubicacionDestinoId, null, null, MotivoCancelacion.SIN_ASIGNAR, null,
                avisoSinAsignarAt, createdAt, Objects.requireNonNull(now, "now"));
    }

    /**
     * RF-13: el administrador cancela el domicilio en cualquier estado salvo entregado. Se conserva quién lo tenía
     * para la supervisión, pero el código de confirmación deja de servir.
     */
    public Domicilio cancelarPorAdministrador(String motivo, Instant now) {
        Objects.requireNonNull(now, "now");
        if (estado == EstadoDomicilio.ENTREGADO) {
            throw new DomainException("INVALID_STATE", "El domicilio ya fue entregado y no se puede cancelar.",
                    Map.of("estadoActual", estado.name()));
        }
        if (estado == EstadoDomicilio.CANCELADO) {
            throw new DomainException("INVALID_STATE", "El domicilio ya está cancelado.",
                    Map.of("estadoActual", estado.name()));
        }
        String limpio = motivoValido(motivo);
        return new Domicilio(id, pedidoId, vendedorId, compradorId, domiciliarioId, EstadoDomicilio.CANCELADO,
                tarifa, ubicacionOrigenId, ubicacionDestinoId, null, asignadoAt, MotivoCancelacion.ADMINISTRADOR,
                limpio, avisoSinAsignarAt, createdAt, now);
    }

    /** Valida el motivo de la cancelación del administrador y lo devuelve sin espacios en los extremos. */
    public static String motivoValido(String motivo) {
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.length() < MOTIVO_MINIMO || limpio.length() > MOTIVO_MAXIMO) {
            String message = "Escribe el motivo de la cancelación (de " + MOTIVO_MINIMO + " a " + MOTIVO_MAXIMO
                    + " caracteres).";
            throw new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of("motivo", message)));
        }
        return limpio;
    }

    /** RF-23: el domiciliario asignado confirma que recogió el pedido en el punto A. */
    public Domicilio confirmarRecogida(Instant now) {
        requireEstado(EstadoDomicilio.ASIGNADO, "Solo se confirma la recogida de un domicilio asignado.");
        return conEstado(EstadoDomicilio.RECOGIDO, now);
    }

    /** RF-23: el vendedor confirma que el domiciliario salió con el pedido. */
    public Domicilio confirmarSalida(Instant now) {
        if (estado == EstadoDomicilio.ASIGNADO) {
            throw new DomainException("INVALID_STATE", "Primero el domiciliario debe confirmar la recogida.",
                    Map.of("estadoActual", estado.name()));
        }
        requireEstado(EstadoDomicilio.RECOGIDO, "Solo se confirma la salida de un domicilio recogido.");
        return conEstado(EstadoDomicilio.EN_CAMINO, now);
    }

    /** RF-24: el domiciliario entregó el pedido; el código ya se verificó. */
    public Domicilio entregar(Instant now) {
        if (estado == EstadoDomicilio.ENTREGADO) {
            throw new DomainException("INVALID_STATE", "La entrega ya fue confirmada.",
                    Map.of("estadoActual", estado.name()));
        }
        requireEstado(EstadoDomicilio.EN_CAMINO, "Solo se confirma la entrega de un domicilio en camino.");
        return conEstado(EstadoDomicilio.ENTREGADO, now);
    }

    /** Compara el código en tiempo constante (RF-24 FR-002). */
    public boolean codigoCoincide(String codigo) {
        if (codigoConfirmacion == null || codigo == null) {
            return false;
        }
        return MessageDigest.isEqual(codigoConfirmacion.getBytes(StandardCharsets.US_ASCII),
                codigo.getBytes(StandardCharsets.US_ASCII));
    }

    /** Estados en los que el cliente puede ver su código (RF-25). */
    public boolean codigoVisible() {
        return EN_CURSO.contains(estado);
    }

    public boolean activo() {
        return estado != EstadoDomicilio.CANCELADO;
    }

    public boolean asignadoA(UUID usuarioId) {
        return domiciliarioId != null && domiciliarioId.equals(usuarioId);
    }

    private Domicilio conEstado(EstadoDomicilio nuevo, Instant now) {
        return new Domicilio(id, pedidoId, vendedorId, compradorId, domiciliarioId, nuevo, tarifa,
                ubicacionOrigenId, ubicacionDestinoId, codigoConfirmacion, asignadoAt, motivoCancelacion,
                detalleCancelacion, avisoSinAsignarAt, createdAt, Objects.requireNonNull(now, "now"));
    }

    private void requireEstado(EstadoDomicilio esperado, String message) {
        if (estado != esperado) {
            throw new DomainException("INVALID_STATE", message, Map.of("estadoActual", estado.name()));
        }
    }

    private void requireDisponible() {
        if (estado != EstadoDomicilio.DISPONIBLE) {
            throw new DomainException("NOT_AVAILABLE", "El domicilio ya no está disponible.",
                    Map.of("estadoActual", estado.name()));
        }
    }
}
