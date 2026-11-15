package co.edu.napa.reportes.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Problema que un usuario informa al administrador (RF-60). Nace {@code PENDIENTE} y el administrador lo cierra con
 * una nota (RF-62); desde {@code RESUELTO} no hay transición.
 */
public final class Reporte {
    private final UUID id;
    private final UUID autorId;
    private final MotivoReporte motivo;
    private final String descripcion;
    private final TipoEntidadReportada tipoEntidad;
    private final UUID entidadId;
    private final EstadoReporte estado;
    private final String notaResolucion;
    private final UUID resueltoPorId;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Reporte(
            UUID id,
            UUID autorId,
            MotivoReporte motivo,
            String descripcion,
            TipoEntidadReportada tipoEntidad,
            UUID entidadId,
            EstadoReporte estado,
            String notaResolucion,
            UUID resueltoPorId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.autorId = Objects.requireNonNull(autorId, "autorId");
        this.motivo = Objects.requireNonNull(motivo, "motivo");
        this.descripcion = Objects.requireNonNull(descripcion, "descripcion");
        if ((tipoEntidad == null) != (entidadId == null)) {
            throw new IllegalArgumentException("tipoEntidad y entidadId van juntos");
        }
        this.tipoEntidad = tipoEntidad;
        this.entidadId = entidadId;
        this.estado = Objects.requireNonNull(estado, "estado");
        this.notaResolucion = notaResolucion;
        this.resueltoPorId = resueltoPorId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Reporte nuevo(UUID autorId, MotivoReporte motivo, String descripcion,
                                TipoEntidadReportada tipoEntidad, UUID entidadId, Instant ahora) {
        if (motivo == null) {
            throw validacion("motivo", "Elige el motivo del reporte.");
        }
        String texto = descripcion == null ? "" : descripcion.strip();
        if (texto.length() < 10 || texto.length() > 500) {
            throw validacion("descripcion", "La descripción debe tener entre 10 y 500 caracteres.");
        }
        if (motivo.requiereEntidad() && entidadId == null) {
            throw validacion("entidad", "Indica el pedido, domicilio o usuario relacionado con el reporte.");
        }
        return new Reporte(UUID.randomUUID(), autorId, motivo, texto, tipoEntidad, entidadId,
                EstadoReporte.PENDIENTE, null, null, ahora, ahora);
    }

    /** RF-62: guarda la nota, el administrador y la fecha. */
    public Reporte resolver(UUID administradorId, String nota, Instant ahora) {
        Objects.requireNonNull(administradorId, "administradorId");
        String texto = nota == null ? "" : nota.strip();
        if (texto.length() < 5 || texto.length() > 1000) {
            throw validacion("nota", "La nota debe tener entre 5 y 1000 caracteres.");
        }
        if (estado == EstadoReporte.RESUELTO) {
            throw new DomainException("INVALID_STATE", "Este reporte ya fue resuelto.",
                    Map.of("estadoActual", estado.name()));
        }
        return new Reporte(id, autorId, motivo, descripcion, tipoEntidad, entidadId, EstadoReporte.RESUELTO, texto,
                administradorId, createdAt, ahora);
    }

    private static DomainException validacion(String campo, String mensaje) {
        return new DomainException("VALIDATION_ERROR", mensaje, Map.of("fields", Map.of(campo, mensaje)));
    }

    public UUID id() {
        return id;
    }

    public UUID autorId() {
        return autorId;
    }

    public MotivoReporte motivo() {
        return motivo;
    }

    public String descripcion() {
        return descripcion;
    }

    public TipoEntidadReportada tipoEntidad() {
        return tipoEntidad;
    }

    public UUID entidadId() {
        return entidadId;
    }

    public EstadoReporte estado() {
        return estado;
    }

    public String notaResolucion() {
        return notaResolucion;
    }

    public UUID resueltoPorId() {
        return resueltoPorId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
