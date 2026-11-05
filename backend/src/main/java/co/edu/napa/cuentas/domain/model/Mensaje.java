package co.edu.napa.cuentas.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de un envío de código de verificación (RF-70). Nunca guarda el código:
 * lo genera y valida el proveedor.
 */
public record Mensaje(
        UUID id,
        CanalVerificacion canal,
        PropositoVerificacion proposito,
        String destino,
        EstadoMensaje estado,
        String proveedorId,
        Instant createdAt,
        Instant updatedAt
) {
    public Mensaje {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(proposito, "proposito");
        Objects.requireNonNull(destino, "destino");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Mensaje nuevo(
            CanalVerificacion canal,
            PropositoVerificacion proposito,
            String destino,
            EstadoMensaje estado,
            String proveedorId,
            Instant now
    ) {
        return new Mensaje(UUID.randomUUID(), canal, proposito, destino, estado, proveedorId, now, now);
    }

    /** Si el envío por WhatsApp falló, la app puede ofrecer SMS como respaldo (RF-70 FR-002). */
    public boolean smsDisponible() {
        return estado == EstadoMensaje.FALLIDO && canal == CanalVerificacion.WHATSAPP;
    }
}