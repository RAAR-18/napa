package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort.DomicilioDePedido;
import co.edu.napa.pedidos.application.ports.in.ContactarDesdePedidoPort.CanalContacto;
import co.edu.napa.pedidos.application.ports.in.ContactarDesdePedidoPort.Destinatario;
import co.edu.napa.pedidos.application.ports.in.ContactarDesdePedidoPort.Tipo;
import co.edu.napa.pedidos.application.ports.out.CanalContactoRepositoryPort;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactarDesdePedidoUseCaseTest {
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();

    private PedidoRepositoryPort pedidos;
    private CanalContactoRepositoryPort canales;
    private ConsultarDomicilioDePedidoPort domicilios;
    private ConsultarContactoUsuarioPort contactos;
    private ContactarDesdePedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoRepositoryPort.class);
        canales = mock(CanalContactoRepositoryPort.class);
        domicilios = mock(ConsultarDomicilioDePedidoPort.class);
        contactos = mock(ConsultarContactoUsuarioPort.class);
        useCase = new ContactarDesdePedidoUseCase(pedidos, canales, domicilios, contactos,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(canales.vendedorDelPedido(PEDIDO)).thenReturn(Optional.of(VENDEDOR));
        when(contactos.consultar(VENDEDOR)).thenReturn(Optional.of(
                new ConsultarContactoUsuarioPort.Contacto("Ana", "+573001234567")));
        when(contactos.consultar(DOMICILIARIO)).thenReturn(Optional.of(
                new ConsultarContactoUsuarioPort.Contacto("Carlos", "+573109876543")));
    }

    private void pedido(EstadoPedido estado) {
        PedidoDetalleProjection detalle = mock(PedidoDetalleProjection.class);
        when(detalle.estado()).thenReturn(estado);
        when(pedidos.consultarDetalleCliente(CLIENTE, PEDIDO)).thenReturn(Optional.of(detalle));
    }

    @Test
    void llamadaAlVendedorDevuelveTelYQuedaRegistrada() {
        pedido(EstadoPedido.ACEPTADO);

        CanalContacto canal = useCase.contactar(CLIENTE, PEDIDO, Destinatario.VENDEDOR, Tipo.LLAMADA);

        assertEquals("tel:+573001234567", canal.enlace());
        verify(canales).registrar(eq(canal.id()), eq(PEDIDO), eq(CLIENTE), eq(VENDEDOR), eq("VENDEDOR"),
                eq("LLAMADA"), eq(AHORA));
    }

    @Test
    void mensajeAlDomiciliarioEnCaminoDevuelveWhatsApp() {
        pedido(EstadoPedido.EN_CAMINO);
        when(domicilios.porPedido(PEDIDO)).thenReturn(Optional.of(
                new DomicilioDePedido(UUID.randomUUID(), "EN_CAMINO", null, AHORA, DOMICILIARIO)));

        CanalContacto canal = useCase.contactar(CLIENTE, PEDIDO, Destinatario.DOMICILIARIO, Tipo.MENSAJE);

        assertEquals("https://wa.me/573109876543", canal.enlace());
    }

    @Test
    void sinDomiciliarioEnCursoOPedidoCerradoNoHayContacto() {
        pedido(EstadoPedido.ACEPTADO);
        when(domicilios.porPedido(PEDIDO)).thenReturn(Optional.of(
                new DomicilioDePedido(UUID.randomUUID(), "DISPONIBLE", null, AHORA, null)));
        assertCodigo("INVALID_STATE", () -> useCase.contactar(CLIENTE, PEDIDO, Destinatario.DOMICILIARIO, Tipo.LLAMADA));

        pedido(EstadoPedido.ENTREGADO);
        assertCodigo("INVALID_STATE", () -> useCase.contactar(CLIENTE, PEDIDO, Destinatario.VENDEDOR, Tipo.LLAMADA));
        verify(canales, never()).registrar(any(), any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    void elPedidoDeOtroClienteNoExiste() {
        assertCodigo("NOT_FOUND", () -> useCase.contactar(UUID.randomUUID(), PEDIDO, Destinatario.VENDEDOR, Tipo.MENSAJE));
    }

    private static void assertCodigo(String codigo, Executable accion) {
        assertEquals(codigo, assertThrows(DomainException.class, accion).code());
    }
}
