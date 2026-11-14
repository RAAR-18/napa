package co.edu.napa.notificaciones.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notificacion(
        UUID id,
        UUID usuarioId,
        String tipo,
        String titulo,
        String cuerpo,
        Instant leidaAt,
        Instant createdAt,
        Referencia referencia
) {
    /** Notificación sin pedido ni domicilio asociado (reportes y comentarios). */
    public Notificacion(UUID id, UUID usuarioId, String tipo, String titulo, String cuerpo, Instant leidaAt,
                        Instant createdAt) {
        this(id, usuarioId, tipo, titulo, cuerpo, leidaAt, createdAt, null);
    }

    /** A qué abre la notificación en la app: el pedido (cliente y vendedor) o el domicilio (domiciliario). */
    public record Referencia(String tipo, UUID id) {
        public static final String PEDIDO = "PEDIDO";
        public static final String DOMICILIO = "DOMICILIO";

        public Referencia {
            Objects.requireNonNull(id, "id");
            if (!PEDIDO.equals(tipo) && !DOMICILIO.equals(tipo)) {
                throw new IllegalArgumentException("Unsupported reference type");
            }
        }
    }

    public Notificacion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(cuerpo, "cuerpo");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
