package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.pedidos.application.ports.in.ConsultarClientePedidoPort.ClientePedido;
import co.edu.napa.pedidos.application.ports.out.PedidoDetalleProjection;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarClientePedidoUseCaseTest {
    private static final UUID VENDEDOR = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();

    private PedidoRepositoryPort pedidos;
    private ConsultarContactoUsuarioPort contactos;
    private ConsultarClientePedidoUseCase useCase;
    private PedidoDetalleProjection detalle;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoRepositoryPort.class);
        contactos = mock(ConsultarContactoUsuarioPort.class);
        useCase = new ConsultarClientePedidoUseCase(pedidos, contactos);
        detalle = mock(PedidoDetalleProjection.class);
        when(detalle.clienteId()).thenReturn(CLIENTE);
        when(detalle.ubicacionEntrega()).thenReturn(
                new PedidoDetalleProjection.Ubicacion(11.24, -74.21, "Calle 15", "Casa azul", null));
        when(pedidos.consultarDetalleVendedor(VENDEDOR, PEDIDO)).thenReturn(Optional.of(detalle));
        when(contactos.consultar(CLIENTE)).thenReturn(Optional.of(
                new ConsultarContactoUsuarioPort.Contacto("Ana", "+573001234567")));
    }

    @Test
    void conElPedidoEnCursoVeCelularYDireccion() {
        when(detalle.estado()).thenReturn(EstadoPedido.ACEPTADO);

        ClientePedido cliente = useCase.consultar(VENDEDOR, PEDIDO);

        assertEquals("+573001234567", cliente.celular());
        assertEquals("Calle 15", cliente.direccionEntrega().direccion());
    }

    @Test
    void conElPedidoCerradoSoloQuedaElNombre() {
        when(detalle.estado()).thenReturn(EstadoPedido.ENTREGADO);

        ClientePedido cliente = useCase.consultar(VENDEDOR, PEDIDO);

        assertEquals("Ana", cliente.nombre());
        assertNull(cliente.celular());
        assertNull(cliente.direccionEntrega());
    }

    @Test
    void laCuentaEliminadaSeMuestraSinContacto() {
        when(detalle.estado()).thenReturn(EstadoPedido.ACEPTADO);
        when(contactos.consultar(CLIENTE)).thenReturn(Optional.of(
                new ConsultarContactoUsuarioPort.Contacto("Ana", "+573001234567", true)));

        ClientePedido cliente = useCase.consultar(VENDEDOR, PEDIDO);

        assertEquals("Cliente eliminado", cliente.nombre());
        assertNull(cliente.celular());
    }

    @Test
    void elPedidoDeOtroVendedorNoExiste() {
        assertEquals("NOT_FOUND", assertThrows(DomainException.class,
                () -> useCase.consultar(UUID.randomUUID(), PEDIDO)).code());
    }
}
