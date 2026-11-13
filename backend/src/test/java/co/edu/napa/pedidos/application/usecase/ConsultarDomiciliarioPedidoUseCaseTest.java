package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort;
import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort.PerfilPublico;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort;
import co.edu.napa.domicilios.application.ports.in.ConsultarDomicilioDePedidoPort.DomicilioDePedido;
import co.edu.napa.pedidos.application.ports.in.ConsultarDomiciliarioPedidoPort.DomiciliarioDePedido;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarDomiciliarioPedidoUseCaseTest {
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();
    private static final UUID DOMICILIO = UUID.randomUUID();
    private static final UUID DOMICILIARIO = UUID.randomUUID();

    private PedidoRepositoryPort pedidos;
    private ConsultarDomicilioDePedidoPort domicilios;
    private ConsultarPerfilPublicoUsuarioPort usuarios;
    private ConsultarDomiciliarioPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoRepositoryPort.class);
        domicilios = mock(ConsultarDomicilioDePedidoPort.class);
        usuarios = mock(ConsultarPerfilPublicoUsuarioPort.class);
        useCase = new ConsultarDomiciliarioPedidoUseCase(pedidos, domicilios, usuarios);
        when(pedidos.consultarDetalleCliente(CLIENTE, PEDIDO))
                .thenReturn(Optional.of(mock(PedidoDetalleProjection.class)));
        when(pedidos.consultarDetalleVendedor(VENDEDOR, PEDIDO))
                .thenReturn(Optional.of(mock(PedidoDetalleProjection.class)));
    }

    @Test
    void elClienteVeNombreYFotoDelDomiciliarioAsignado() {
        when(domicilios.porPedido(PEDIDO)).thenReturn(Optional.of(
                new DomicilioDePedido(DOMICILIO, "EN_CAMINO", null, Instant.now(), DOMICILIARIO)));
        when(usuarios.consultarPerfil(DOMICILIARIO))
                .thenReturn(Optional.of(new PerfilPublico("Carlos Ruiz", "https://cdn/foto.jpg")));

        DomiciliarioDePedido result = useCase.consultarComoCliente(CLIENTE, PEDIDO);

        assertEquals(DOMICILIO, result.domicilioId());
        assertEquals("EN_CAMINO", result.estadoDomicilio());
        assertEquals("Carlos Ruiz", result.domiciliario().nombre());
        assertEquals("https://cdn/foto.jpg", result.domiciliario().fotoUrl());
        assertNull(result.domiciliario().calificacion());
    }

    @Test
    void elVendedorTambienLoVe() {
        when(domicilios.porPedido(PEDIDO)).thenReturn(Optional.of(
                new DomicilioDePedido(DOMICILIO, "ASIGNADO", null, Instant.now(), DOMICILIARIO)));
        when(usuarios.consultarPerfil(DOMICILIARIO)).thenReturn(Optional.of(new PerfilPublico("Carlos Ruiz", null)));

        assertEquals("Carlos Ruiz", useCase.consultarComoVendedor(VENDEDOR, PEDIDO).domiciliario().nombre());
    }

    @Test
    void sinAceptarTodaviaNoHayDomiciliario() {
        when(domicilios.porPedido(PEDIDO)).thenReturn(Optional.of(
                new DomicilioDePedido(DOMICILIO, "DISPONIBLE", null, Instant.now())));

        DomiciliarioDePedido result = useCase.consultarComoCliente(CLIENTE, PEDIDO);

        assertEquals("DISPONIBLE", result.estadoDomicilio());
        assertNull(result.domiciliario());
        verify(usuarios, never()).consultarPerfil(any());
    }

    @Test
    void unPedidoSinDomicilioNoTieneDomiciliario() {
        when(domicilios.porPedido(PEDIDO)).thenReturn(Optional.empty());

        DomiciliarioDePedido result = useCase.consultarComoCliente(CLIENTE, PEDIDO);

        assertNull(result.domicilioId());
        assertNull(result.domiciliario());
    }

    @Test
    void unPedidoAjenoNoEstaDisponible() {
        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarComoCliente(UUID.randomUUID(), PEDIDO));

        assertEquals("NOT_FOUND", error.code());
        verify(domicilios, never()).porPedido(any());
    }
}
