package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.IntentosCodigoEntregaPort;
import co.edu.napa.domicilios.application.ports.out.UbicacionDomiciliarioPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RF-26 FR-003: posición del domiciliario en camino. */
class UbicacionDomiciliarioTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID DOMICILIARIO = UUID.randomUUID();
    private static final UUID COMPRADOR = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();

    private DomicilioRepositoryPort domicilios;
    private UbicacionDomiciliarioPort ubicaciones;
    private EjecucionDomicilioUseCase useCase;
    private Domicilio asignado;
    private Domicilio enCamino;

    @BeforeEach
    void setUp() {
        domicilios = mock(DomicilioRepositoryPort.class);
        ubicaciones = mock(UbicacionDomiciliarioPort.class);
        useCase = new EjecucionDomicilioUseCase(domicilios, mock(PedidoParaDomicilioPort.class),
                mock(IntentosCodigoEntregaPort.class), mock(EventPublisherPort.class), ubicaciones,
                Clock.fixed(NOW, ZoneOffset.UTC));
        asignado = Domicilio.publicar(UUID.randomUUID(), VENDEDOR, COMPRADOR, 4_500, UUID.randomUUID(),
                UUID.randomUUID(), NOW.minusSeconds(1_200)).asignar(DOMICILIARIO, "048213", NOW.minusSeconds(600));
        enCamino = asignado.confirmarRecogida(NOW.minusSeconds(300)).confirmarSalida(NOW.minusSeconds(200));
        when(domicilios.buscar(enCamino.id())).thenReturn(Optional.of(enCamino));
        when(domicilios.listarEventos(enCamino.id())).thenReturn(List.of());
        when(ubicaciones.guardar(any(), anyDouble(), anyDouble(), any())).thenReturn(true);
    }

    @Test
    void elDomiciliarioEnCaminoEnviaYElClienteLaVeRedondeada() {
        useCase.actualizarUbicacion(DOMICILIARIO, enCamino.id(), 11.24537, -74.20612);
        verify(ubicaciones).guardar(enCamino.id(), 11.24537, -74.20612, NOW);

        when(ubicaciones.consultar(enCamino.id())).thenReturn(Optional.of(
                new UbicacionDomiciliarioPort.Posicion(11.24537, -74.20612, NOW)));
        var cliente = useCase.consultarTrazabilidad(COMPRADOR, enCamino.id()).ubicacionDomiciliario();
        assertEquals(11.245, cliente.latitud());
        assertEquals(-74.206, cliente.longitud());
        assertNull(useCase.consultarTrazabilidad(VENDEDOR, enCamino.id()).ubicacionDomiciliario());
    }

    @Test
    void reglasDeEnvio() {
        assertCodigo("FORBIDDEN", () -> useCase.actualizarUbicacion(UUID.randomUUID(), enCamino.id(), 1, 1));
        assertCodigo("VALIDATION_ERROR", () -> useCase.actualizarUbicacion(DOMICILIARIO, enCamino.id(), 95, 1));
        when(domicilios.buscar(asignado.id())).thenReturn(Optional.of(asignado));
        assertCodigo("INVALID_STATE", () -> useCase.actualizarUbicacion(DOMICILIARIO, asignado.id(), 1, 1));
        when(domicilios.buscar(enCamino.id())).thenReturn(Optional.of(enCamino));
        when(ubicaciones.guardar(any(), anyDouble(), anyDouble(), any())).thenReturn(false);
        assertCodigo("RATE_LIMITED", () -> useCase.actualizarUbicacion(DOMICILIARIO, enCamino.id(), 1, 1));
    }

    private static void assertCodigo(String codigo, Executable accion) {
        assertEquals(codigo, assertThrows(DomainException.class, accion).code());
    }
}
