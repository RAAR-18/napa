package co.edu.napa.reportes.application.usecase;

import co.edu.napa.reportes.application.ports.in.CrearReportePort.NuevoReporte;
import co.edu.napa.reportes.application.ports.out.RelacionesReportePort;
import co.edu.napa.reportes.application.ports.out.ReporteRepositoryPort;
import co.edu.napa.reportes.domain.event.ReporteCreadoEvent;
import co.edu.napa.reportes.domain.event.ReporteResueltoEvent;
import co.edu.napa.reportes.domain.model.EstadoReporte;
import co.edu.napa.reportes.domain.model.MotivoReporte;
import co.edu.napa.reportes.domain.model.Reporte;
import co.edu.napa.reportes.domain.model.TipoEntidadReportada;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-60, RF-61 y RF-62. */
class ReporteUseCasesTest {
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID AUTOR = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID DOMICILIO = UUID.randomUUID();
    private static final String DESCRIPCION = "Esperé 15 minutos en la dirección y no contestó.";

    private ReporteRepositoryPort reportes;
    private RelacionesReportePort relaciones;
    private EventPublisherPort eventos;
    private CrearReporteUseCase crear;
    private ResolverReporteUseCase resolver;
    private ConsultarReportesUseCase consultar;

    @BeforeEach
    void setUp() {
        reportes = mock(ReporteRepositoryPort.class);
        relaciones = mock(RelacionesReportePort.class);
        eventos = mock(EventPublisherPort.class);
        Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);
        crear = new CrearReporteUseCase(reportes, relaciones, eventos, clock);
        resolver = new ResolverReporteUseCase(reportes, eventos, clock);
        consultar = new ConsultarReportesUseCase(reportes, relaciones);
    }

    @Test
    void elDomiciliarioReportaUnDomicilioSuyoYSeAvisaALosAdministradores() {
        when(relaciones.participaEnDomicilio(AUTOR, DOMICILIO)).thenReturn(true);

        Reporte reporte = crear.crear(AUTOR, "DOMICILIARIO",
                new NuevoReporte(MotivoReporte.DOMICILIO, "  " + DESCRIPCION + " ", null, DOMICILIO, null));

        assertEquals(EstadoReporte.PENDIENTE, reporte.estado());
        assertEquals(DESCRIPCION, reporte.descripcion());
        assertEquals(TipoEntidadReportada.DOMICILIO, reporte.tipoEntidad());
        verify(reportes).insertar(reporte);
        ArgumentCaptor<ReporteCreadoEvent> evento = ArgumentCaptor.forClass(ReporteCreadoEvent.class);
        verify(eventos).publish(evento.capture());
        assertEquals("reportes.reporte-creado", evento.getValue().type());
        assertEquals(AUTOR, evento.getValue().payload().get("autorId"));
    }

    @Test
    void unReporteDePlataformaNoNecesitaEntidad() {
        Reporte reporte = crear.crear(AUTOR, "CLIENTE",
                new NuevoReporte(MotivoReporte.PLATAFORMA, DESCRIPCION, null, null, null));

        assertNull(reporte.tipoEntidad());
    }

    @Test
    void validaMotivoDescripcionYEntidades() {
        assertCodigo("VALIDATION_ERROR", () -> crear.crear(AUTOR, "CLIENTE",
                new NuevoReporte(MotivoReporte.OTRO, "corta", null, null, null)));
        assertCodigo("VALIDATION_ERROR", () -> crear.crear(AUTOR, "CLIENTE",
                new NuevoReporte(MotivoReporte.PEDIDO, DESCRIPCION, null, null, null)));
        assertCodigo("VALIDATION_ERROR", () -> crear.crear(AUTOR, "CLIENTE",
                new NuevoReporte(MotivoReporte.OTRO, DESCRIPCION, UUID.randomUUID(), DOMICILIO, null)));
        assertCodigo("FORBIDDEN", () -> crear.crear(AUTOR, "ADMINISTRADOR",
                new NuevoReporte(MotivoReporte.OTRO, DESCRIPCION, null, null, null)));
    }

    @Test
    void loAjenoRespondeNotFoundYNadieSeReportaASiMismo() {
        assertCodigo("NOT_FOUND", () -> crear.crear(AUTOR, "CLIENTE",
                new NuevoReporte(MotivoReporte.PEDIDO, DESCRIPCION, UUID.randomUUID(), null, null)));
        when(relaciones.compartieronPedido(AUTOR, AUTOR)).thenReturn(true);
        assertCodigo("NOT_FOUND", () -> crear.crear(AUTOR, "CLIENTE",
                new NuevoReporte(MotivoReporte.COMPORTAMIENTO_USUARIO, DESCRIPCION, null, null, AUTOR)));
        verify(reportes, never()).insertar(any());
    }

    @Test
    void unReporteAbiertoIgualRespondeConflict() {
        when(relaciones.participaEnDomicilio(AUTOR, DOMICILIO)).thenReturn(true);
        when(reportes.existeAbierto(AUTOR, MotivoReporte.DOMICILIO, TipoEntidadReportada.DOMICILIO, DOMICILIO))
                .thenReturn(true);

        assertCodigo("CONFLICT", () -> crear.crear(AUTOR, "DOMICILIARIO",
                new NuevoReporte(MotivoReporte.DOMICILIO, DESCRIPCION, null, DOMICILIO, null)));
    }

    @Test
    void elAdministradorResuelveUnaSolaVezYSeAvisaAlAutor() {
        Reporte pendiente = Reporte.nuevo(AUTOR, MotivoReporte.OTRO, DESCRIPCION, null, null, AHORA);
        when(reportes.buscarParaActualizar(pendiente.id())).thenReturn(Optional.of(pendiente));

        Reporte resuelto = resolver.resolver(ADMIN, pendiente.id(), " Se contactó al cliente. ");

        assertEquals(EstadoReporte.RESUELTO, resuelto.estado());
        assertEquals("Se contactó al cliente.", resuelto.notaResolucion());
        assertEquals(ADMIN, resuelto.resueltoPorId());
        verify(reportes).actualizar(resuelto);
        ArgumentCaptor<ReporteResueltoEvent> evento = ArgumentCaptor.forClass(ReporteResueltoEvent.class);
        verify(eventos).publish(evento.capture());
        assertEquals(AUTOR, evento.getValue().payload().get("autorId"));

        when(reportes.buscarParaActualizar(pendiente.id())).thenReturn(Optional.of(resuelto));
        DomainException e = assertCodigo("INVALID_STATE", () -> resolver.resolver(ADMIN, pendiente.id(), "Otra nota"));
        assertEquals("RESUELTO", e.details().get("estadoActual"));
        assertCodigo("VALIDATION_ERROR", () -> resolver.resolver(ADMIN, pendiente.id(), "ok"));
        assertCodigo("NOT_FOUND", () -> resolver.resolver(ADMIN, UUID.randomUUID(), "Nota válida"));
    }

    @Test
    void elDetalleMarcaNoDisponibleLaEntidadQueYaNoExiste() {
        Reporte reporte = Reporte.nuevo(AUTOR, MotivoReporte.DOMICILIO, DESCRIPCION,
                TipoEntidadReportada.DOMICILIO, DOMICILIO, AHORA);
        when(reportes.buscar(reporte.id())).thenReturn(Optional.of(reporte));
        when(relaciones.usuario(AUTOR)).thenReturn(Optional.of(
                new RelacionesReportePort.Usuario(AUTOR, "Carlos", "DOMICILIARIO", "ACTIVA")));

        var detalle = consultar.detalle(reporte.id());

        assertEquals("Carlos", detalle.autor().nombre());
        assertFalse(detalle.entidad().disponible());
        assertNull(detalle.resueltoPor());

        when(relaciones.resumenDomicilio(DOMICILIO)).thenReturn(Optional.of(Map.of("estado", "EN_CAMINO")));
        assertTrue(consultar.detalle(reporte.id()).entidad().disponible());
    }

    private static DomainException assertCodigo(String codigo, Executable accion) {
        DomainException e = assertThrows(DomainException.class, accion);
        assertEquals(codigo, e.code());
        return e;
    }
}
