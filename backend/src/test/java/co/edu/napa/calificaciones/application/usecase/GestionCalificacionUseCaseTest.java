package co.edu.napa.calificaciones.application.usecase;

import co.edu.napa.calificaciones.application.ports.out.CalificacionRepositoryPort;
import co.edu.napa.calificaciones.domain.event.ComentarioModeradoEvent;
import co.edu.napa.calificaciones.domain.model.AccionModeracion;
import co.edu.napa.calificaciones.domain.model.Calificacion;
import co.edu.napa.calificaciones.domain.model.EstadoCalificacion;
import co.edu.napa.calificaciones.domain.model.TipoEntidad;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-04, RF-05 y RF-06. */
class GestionCalificacionUseCaseTest {
    private static final Instant ANTES = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID AUTOR = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private CalificacionRepositoryPort repositorio;
    private EventPublisherPort eventos;
    private CalificacionPropiaUseCase propias;
    private ModerarCalificacionUseCase moderar;
    private Calificacion activa;

    @BeforeEach
    void setUp() {
        repositorio = mock(CalificacionRepositoryPort.class);
        eventos = mock(EventPublisherPort.class);
        Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);
        propias = new CalificacionPropiaUseCase(repositorio, clock);
        moderar = new ModerarCalificacionUseCase(repositorio, eventos, clock);
        activa = new Calificacion(UUID.randomUUID(), AUTOR, UUID.randomUUID(), TipoEntidad.EMPRENDIMIENTO,
                UUID.randomUUID(), 4, "Buen servicio", EstadoCalificacion.ACTIVA, false, ANTES, ANTES);
        when(repositorio.buscarParaActualizar(activa.id())).thenReturn(Optional.of(activa));
    }

    @Test
    void elAutorEditaPuntajeYComentario() {
        Calificacion editada = propias.editar(AUTOR, activa.id(), 2, "Llegó tarde");

        assertEquals(2, editada.puntaje());
        assertEquals("Llegó tarde", editada.comentario());
        assertEquals(AHORA, editada.updatedAt());
        verify(repositorio).actualizar(editada);
    }

    @Test
    void editarConLosMismosValoresNoTocaUpdatedAt() {
        Calificacion igual = propias.editar(AUTOR, activa.id(), 4, "Buen servicio");

        assertSame(activa, igual);
        verify(repositorio, never()).actualizar(any());
    }

    @Test
    void otroUsuarioNoEditaNiElimina() {
        assertCodigo("FORBIDDEN", () -> propias.editar(UUID.randomUUID(), activa.id(), 3, null));
        assertCodigo("FORBIDDEN", () -> propias.eliminar(UUID.randomUUID(), activa.id()));
    }

    @Test
    void editarSinCamposOConComentarioVacioSeRechaza() {
        assertCodigo("VALIDATION_ERROR", () -> propias.editar(AUTOR, activa.id(), null, null));
        assertCodigo("VALIDATION_ERROR", () -> propias.editar(AUTOR, activa.id(), null, "   "));
    }

    @Test
    void elAutorEliminaYYaNoPuedeEditar() {
        Calificacion eliminada = propias.eliminar(AUTOR, activa.id());
        assertEquals(EstadoCalificacion.ELIMINADA_AUTOR, eliminada.estado());
        when(repositorio.buscarParaActualizar(activa.id())).thenReturn(Optional.of(eliminada));

        DomainException e = assertCodigo("INVALID_STATE", () -> propias.editar(AUTOR, activa.id(), 5, null));
        assertEquals("ELIMINADA_AUTOR", e.details().get("estadoActual"));
        assertCodigo("INVALID_STATE", () -> propias.eliminar(AUTOR, activa.id()));
    }

    @Test
    void laCalificacionInexistenteRespondeNotFound() {
        assertCodigo("NOT_FOUND", () -> propias.eliminar(AUTOR, UUID.randomUUID()));
        assertCodigo("NOT_FOUND", () -> moderar.eliminar(ADMIN, UUID.randomUUID(), "Lenguaje ofensivo"));
    }

    @Test
    void elAdministradorEliminaRegistraLaModeracionYAvisaAlAutor() {
        Calificacion eliminada = moderar.eliminar(ADMIN, activa.id(), "  Lenguaje ofensivo ");

        assertEquals(EstadoCalificacion.ELIMINADA_MODERACION, eliminada.estado());
        verify(repositorio).actualizar(eliminada);
        verify(repositorio).registrarModeracion(activa.id(), ADMIN, AccionModeracion.ELIMINADO,
                "Lenguaje ofensivo", "Buen servicio", AHORA);
        ArgumentCaptor<ComentarioModeradoEvent> evento = ArgumentCaptor.forClass(ComentarioModeradoEvent.class);
        verify(eventos).publish(evento.capture());
        assertEquals("calificaciones.comentario-moderado", evento.getValue().type());
        assertEquals(AUTOR, evento.getValue().payload().get("autorId"));
        assertEquals("ELIMINADO", evento.getValue().payload().get("accion"));
        assertEquals("Lenguaje ofensivo", evento.getValue().payload().get("motivo"));
    }

    @Test
    void elAdministradorEditaSoloElTextoYQuedaMarcadaModerada() {
        Calificacion editada = moderar.editarTexto(ADMIN, activa.id(), "Buen servicio.", "Se corrigió el texto");

        assertEquals(4, editada.puntaje());
        assertEquals("Buen servicio.", editada.comentario());
        assertEquals(EstadoCalificacion.ACTIVA, editada.estado());
        assertTrue(editada.moderada());
        verify(repositorio).registrarModeracion(activa.id(), ADMIN, AccionModeracion.EDITADO,
                "Se corrigió el texto", "Buen servicio", AHORA);
    }

    @Test
    void moderarExigeMotivoTextoDistintoYComentario() {
        assertCodigo("VALIDATION_ERROR", () -> moderar.eliminar(ADMIN, activa.id(), "malo"));
        assertCodigo("VALIDATION_ERROR", () -> moderar.editarTexto(ADMIN, activa.id(), "Buen servicio", "Motivo válido"));
        Calificacion sinComentario = new Calificacion(UUID.randomUUID(), AUTOR, UUID.randomUUID(),
                TipoEntidad.PRODUCTO, UUID.randomUUID(), 5, null, EstadoCalificacion.ACTIVA, false, ANTES, ANTES);
        when(repositorio.buscarParaActualizar(sinComentario.id())).thenReturn(Optional.of(sinComentario));
        assertCodigo("BUSINESS_RULE", () -> moderar.eliminar(ADMIN, sinComentario.id(), "Motivo válido"));
        verify(eventos, never()).publish(any());
    }

    private static DomainException assertCodigo(String codigo, Executable accion) {
        DomainException e = assertThrows(DomainException.class, accion);
        assertEquals(codigo, e.code());
        return e;
    }
}
