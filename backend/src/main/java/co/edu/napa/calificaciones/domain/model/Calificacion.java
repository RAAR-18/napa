package co.edu.napa.calificaciones.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puntaje de 1 a 5 y comentario opcional de hasta 300 caracteres (RF-01 FR-008) que un autor deja a una de las
 * partes de un pedido entregado. Solo una calificación {@code ACTIVA} se edita, se elimina o se modera.
 */
public final class Calificacion {
    public static final int COMENTARIO_MAXIMO = 300;

    private final UUID id;
    private final UUID autorId;
    private final UUID pedidoId;
    private final TipoEntidad tipoEntidad;
    private final UUID entidadId;
    private final int puntaje;
    private final String comentario;
    private final EstadoCalificacion estado;
    private final boolean moderada;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Calificacion(
            UUID id,
            UUID autorId,
            UUID pedidoId,
            TipoEntidad tipoEntidad,
            UUID entidadId,
            int puntaje,
            String comentario,
            EstadoCalificacion estado,
            boolean moderada,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.autorId = Objects.requireNonNull(autorId, "autorId");
        this.pedidoId = Objects.requireNonNull(pedidoId, "pedidoId");
        this.tipoEntidad = Objects.requireNonNull(tipoEntidad, "tipoEntidad");
        this.entidadId = Objects.requireNonNull(entidadId, "entidadId");
        this.puntaje = validarPuntaje(puntaje);
        this.comentario = comentario;
        this.estado = Objects.requireNonNull(estado, "estado");
        this.moderada = moderada;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Calificacion nueva(
            UUID autorId,
            UUID pedidoId,
            TipoEntidad tipoEntidad,
            UUID entidadId,
            int puntaje,
            String comentario,
            Instant ahora
    ) {
        return new Calificacion(UUID.randomUUID(), autorId, pedidoId, tipoEntidad, entidadId, puntaje,
                comentarioOpcional(comentario), EstadoCalificacion.ACTIVA, false, ahora, ahora);
    }

    /** RF-04: el autor cambia el puntaje, el comentario o ambos; {@code null} deja el valor actual. */
    public Calificacion editar(Integer nuevoPuntaje, String nuevoComentario, Instant ahora) {
        exigirActiva();
        int puntajeFinal = nuevoPuntaje == null ? puntaje : validarPuntaje(nuevoPuntaje);
        String comentarioFinal = nuevoComentario == null ? comentario : comentarioObligatorio(nuevoComentario);
        if (puntajeFinal == puntaje && Objects.equals(comentarioFinal, comentario)) {
            return this;
        }
        return copia(puntajeFinal, comentarioFinal, EstadoCalificacion.ACTIVA, moderada, ahora);
    }

    /** RF-05: sale de la lista y del promedio; el puntaje y el texto se conservan. */
    public Calificacion eliminarPorAutor(Instant ahora) {
        exigirActiva();
        return copia(puntaje, comentario, EstadoCalificacion.ELIMINADA_AUTOR, moderada, ahora);
    }

    /** RF-06 FR-002: el administrador cambia solo el texto; el puntaje no se toca. */
    public Calificacion moderarTexto(String nuevoComentario, Instant ahora) {
        exigirModerable();
        String texto = comentarioObligatorio(nuevoComentario);
        if (texto.equals(comentario)) {
            throw validacion("comentario", "El nuevo texto es igual al actual.");
        }
        return copia(puntaje, texto, EstadoCalificacion.ACTIVA, true, ahora);
    }

    /** RF-06 FR-001: la calificación deja de mostrarse y sale del promedio (FR-003). */
    public Calificacion eliminarPorModeracion(Instant ahora) {
        exigirModerable();
        return copia(puntaje, comentario, EstadoCalificacion.ELIMINADA_MODERACION, moderada, ahora);
    }

    public static String validarMotivoModeracion(String motivo) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < 5 || texto.length() > 200) {
            throw validacion("motivo", "El motivo debe tener entre 5 y 200 caracteres.");
        }
        return texto;
    }

    private void exigirModerable() {
        exigirActiva();
        if (comentario == null) {
            throw new DomainException("BUSINESS_RULE", "No hay comentario que moderar.");
        }
    }

    private void exigirActiva() {
        if (estado != EstadoCalificacion.ACTIVA) {
            throw new DomainException("INVALID_STATE", "Esta calificación ya fue eliminada.",
                    Map.of("estadoActual", estado.name()));
        }
    }

    private Calificacion copia(int nuevoPuntaje, String nuevoComentario, EstadoCalificacion nuevoEstado,
                               boolean nuevaModerada, Instant ahora) {
        return new Calificacion(id, autorId, pedidoId, tipoEntidad, entidadId, nuevoPuntaje, nuevoComentario,
                nuevoEstado, nuevaModerada, createdAt, Objects.requireNonNull(ahora, "ahora"));
    }

    private static int validarPuntaje(int puntaje) {
        if (puntaje < 1 || puntaje > 5) {
            throw validacion("puntaje", "El puntaje debe ser un número entero de 1 a 5.");
        }
        return puntaje;
    }

    private static String comentarioOpcional(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        return comentarioObligatorio(comentario);
    }

    private static String comentarioObligatorio(String comentario) {
        String texto = comentario == null ? "" : comentario.strip();
        if (texto.isEmpty() || texto.length() > COMENTARIO_MAXIMO) {
            throw validacion("comentario", "El comentario debe tener entre 1 y 300 caracteres.");
        }
        return texto;
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

    public UUID pedidoId() {
        return pedidoId;
    }

    public TipoEntidad tipoEntidad() {
        return tipoEntidad;
    }

    public UUID entidadId() {
        return entidadId;
    }

    public int puntaje() {
        return puntaje;
    }

    public String comentario() {
        return comentario;
    }

    public EstadoCalificacion estado() {
        return estado;
    }

    public boolean moderada() {
        return moderada;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
