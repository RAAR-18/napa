package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort.Detalle;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort.Fila;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort.Filtro;
import co.edu.napa.domicilios.application.ports.in.SupervisarDomiciliosPort.Pagina;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.DomicilioSupervisionProjection;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EventoTrazabilidad;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort.PagoTarifa;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort.ResumenPedido;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupervisarDomiciliosUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();

    private DomicilioRepositoryPort domicilios;
    private PedidoParaDomicilioPort pedidos;
    private PagoTarifaDomicilioPort pagos;
    private ConsultarNombresUsuarioPort usuarios;
    private SupervisarDomiciliosUseCase useCase;

    @BeforeEach
    void setUp() {
        domicilios = mock(DomicilioRepositoryPort.class);
        pedidos = mock(PedidoParaDomicilioPort.class);
        pagos = mock(PagoTarifaDomicilioPort.class);
        usuarios = mock(ConsultarNombresUsuarioPort.class);
        useCase = new SupervisarDomiciliosUseCase(domicilios, pedidos, pagos, usuarios,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Domicilio disponibleHace(long minutos) {
        return Domicilio.publicar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5_000,
                UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(minutos * 60));
    }

    private static DomicilioSupervisionProjection conPuntos(Domicilio domicilio) {
        Ubicacion origen = new Ubicacion(domicilio.ubicacionOrigenId(), 11.2408, -74.2110, "Carrera 5 # 10-20",
                null, NOW, NOW);
        Ubicacion destino = new Ubicacion(domicilio.ubicacionDestinoId(), 11.2501, -74.2002, "Calle 15 # 3-20",
                null, "El Prado", NOW, NOW);
        return new DomicilioSupervisionProjection(domicilio, origen, destino);
    }

    @Test
    void listaTodosLosDomiciliosConNombresEnUnSoloLote() {
        Domicilio disponible = disponibleHace(42);
        Domicilio asignado = disponibleHace(90).asignar(DOMICILIARIO, "048213", NOW.minusSeconds(600));
        when(domicilios.listarGeneral(Filtro.SIN_FILTROS, 0, 20))
                .thenReturn(List.of(conPuntos(disponible), conPuntos(asignado)));
        when(domicilios.contarGeneral(Filtro.SIN_FILTROS)).thenReturn(2L);
        when(usuarios.consultar(any())).thenReturn(Map.of(
                disponible.vendedorId(), "Rosa Martínez", disponible.compradorId(), "Ana Pérez",
                DOMICILIARIO, "Luis Gómez"));

        Pagina pagina = useCase.listar(null, 0, 20);

        assertEquals(2, pagina.totalElements());
        Fila primera = pagina.content().get(0);
        assertEquals(42L, primera.minutosSinTomar());
        assertEquals("Rosa Martínez", primera.vendedor().nombre());
        assertEquals("Ana Pérez", primera.cliente().nombre());
        assertNull(primera.domiciliario());
        assertEquals("Calle 15 # 3-20", primera.destino().direccion());
        assertEquals("El Prado", primera.destino().barrio());
        Fila segunda = pagina.content().get(1);
        assertNull(segunda.minutosSinTomar());
        assertEquals("Luis Gómez", segunda.domiciliario().nombre());
        verify(usuarios, times(1)).consultar(any());
    }

    @Test
    void ningunCampoDelListadoNiDelDetalleExponeElCodigo() {
        assertFalse(Arrays.stream(Fila.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("codigo")));
        assertFalse(Arrays.stream(Detalle.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("codigo")));
    }

    @Test
    void sinDomiciliosDevuelveUnaPaginaVaciaSinPedirNombres() {
        when(domicilios.listarGeneral(any(), anyLong(), anyInt())).thenReturn(List.of());

        Pagina pagina = useCase.listar(Filtro.SIN_FILTROS, 0, 20);

        assertEquals(0, pagina.content().size());
        verify(usuarios, never()).consultar(any());
    }

    @Test
    void validaLaPaginaYElRangoDeFechas() {
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.listar(null, 0, 101)).code());
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.listar(null, -1, 20)).code());
        Filtro alReves = new Filtro(null, NOW, NOW.minusSeconds(1), null, null, false, false);
        assertEquals("VALIDATION_ERROR",
                assertThrows(DomainException.class, () -> useCase.listar(alReves, 0, 20)).code());
    }

    @Test
    void elDetalleIncluyePedidoPagoMotivoYCronologiaConAutores() {
        Domicilio asignado = disponibleHace(90).asignar(DOMICILIARIO, "048213", NOW.minusSeconds(600));
        Domicilio cancelado = asignado.cancelarPorAdministrador("El cliente reportó mal uso", NOW.minusSeconds(60));
        when(domicilios.buscarConPuntos(cancelado.id())).thenReturn(Optional.of(conPuntos(cancelado)));
        when(domicilios.listarEventos(cancelado.id())).thenReturn(List.of(
                EventoTrazabilidad.de(disponibleHace(90), cancelado.vendedorId()),
                EventoTrazabilidad.de(asignado, DOMICILIARIO),
                EventoTrazabilidad.de(cancelado, ADMIN)));
        when(usuarios.consultar(Set.of(cancelado.vendedorId(), cancelado.compradorId(), DOMICILIARIO, ADMIN)))
                .thenReturn(Map.of(ADMIN, "Soporte Ñapa", DOMICILIARIO, "Luis Gómez"));
        when(pedidos.resumen(cancelado.pedidoId(), cancelado.compradorId())).thenReturn(Optional.of(
                new ResumenPedido(cancelado.pedidoId(), "DOMICILIO", "CANCELADO", 14_500, 3)));
        when(pagos.consultar(cancelado.id())).thenReturn(Optional.empty());

        Detalle detalle = useCase.consultar(cancelado.id());

        assertEquals("ADMINISTRADOR", detalle.motivoCancelacion());
        assertEquals("El cliente reportó mal uso", detalle.detalleCancelacion());
        assertEquals(3, detalle.pedido().cantidadItems());
        assertNull(detalle.pagoTarifa());
        assertEquals("Luis Gómez", detalle.domiciliario().nombre());
        assertEquals(3, detalle.trazabilidad().size());
        assertEquals("Soporte Ñapa", detalle.trazabilidad().get(2).autor().nombre());
    }

    @Test
    void elDetalleMuestraElPagoDeLaTarifaYUnIdInexistenteDaNotFound() {
        Domicilio entregado = disponibleHace(90).asignar(DOMICILIARIO, "048213", NOW.minusSeconds(600))
                .confirmarRecogida(NOW.minusSeconds(500)).confirmarSalida(NOW.minusSeconds(400))
                .entregar(NOW.minusSeconds(60));
        when(domicilios.buscarConPuntos(entregado.id())).thenReturn(Optional.of(conPuntos(entregado)));
        when(domicilios.listarEventos(entregado.id())).thenReturn(List.of());
        when(usuarios.consultar(any())).thenReturn(Map.of());
        when(pagos.consultar(entregado.id())).thenReturn(Optional.of(
                new PagoTarifa(UUID.randomUUID(), "REGISTRADO", 5_000, "CUENTA_BANCARIA", NOW, null)));

        Detalle detalle = useCase.consultar(entregado.id());
        assertEquals("REGISTRADO", detalle.pagoTarifa().estado());
        assertNull(detalle.pedido());

        UUID inexistente = UUID.randomUUID();
        when(domicilios.buscarConPuntos(inexistente)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND",
                assertThrows(DomainException.class, () -> useCase.consultar(inexistente)).code());
    }
}
