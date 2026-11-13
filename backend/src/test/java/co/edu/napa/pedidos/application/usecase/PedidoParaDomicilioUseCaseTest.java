package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.event.PedidoEntregadoEvent;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ItemPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.productos.application.ports.in.DescontarInventarioPort.LineaPedido;
import co.edu.napa.productos.application.ports.in.DevolverInventarioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainEvent;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoParaDomicilioUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-13T18:00:00Z");
    private static final UUID PRODUCTO = UUID.randomUUID();

    private final PedidoRepositoryPort pedidos = mock(PedidoRepositoryPort.class);
    private final DevolverInventarioPort inventario = mock(DevolverInventarioPort.class);
    private final EventPublisherPort eventos = mock(EventPublisherPort.class);
    private final PedidoParaDomicilioUseCase useCase =
            new PedidoParaDomicilioUseCase(pedidos, inventario, eventos, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void cancelaElPedidoYDevuelveElInventarioDescontado() {
        Pedido pedido = aceptado(null);
        when(pedidos.bloquearPorId(pedido.id())).thenReturn(Optional.of(pedido));

        useCase.cancelarPorDomicilio(pedido.id());

        ArgumentCaptor<Pedido> actualizado = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).actualizar(actualizado.capture());
        assertEquals(EstadoPedido.CANCELADO, actualizado.getValue().estado());
        verify(inventario).devolver(List.of(new LineaPedido(PRODUCTO, new BigDecimal("2.50"))));
    }

    @Test
    void unaReservaNoDevuelveInventarioPorqueNoLoDesconto() {
        Pedido pedido = aceptado(NOW.plusSeconds(3_600));
        when(pedidos.bloquearPorId(pedido.id())).thenReturn(Optional.of(pedido));

        useCase.cancelarPorDomicilio(pedido.id());

        verify(inventario, never()).devolver(any());
    }

    @Test
    void laSalidaPoneElPedidoEnCamino() {
        Pedido pedido = aceptado(null);
        when(pedidos.bloquearPorId(pedido.id())).thenReturn(Optional.of(pedido));

        useCase.ponerEnCaminoPorDomicilio(pedido.id());

        ArgumentCaptor<Pedido> actualizado = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).actualizar(actualizado.capture());
        assertEquals(EstadoPedido.EN_CAMINO, actualizado.getValue().estado());
        assertEquals(NOW, actualizado.getValue().updatedAt());
    }

    @Test
    void laEntregaCierraElPedidoYPublicaE07ConElDomicilio() {
        Pedido pedido = aceptado(null).ponerEnCaminoPorDomicilio(NOW.minusSeconds(60));
        UUID domicilioId = UUID.randomUUID();
        UUID domiciliario = UUID.randomUUID();
        when(pedidos.bloquearPorId(pedido.id())).thenReturn(Optional.of(pedido));

        useCase.entregarPorDomicilio(pedido.id(), domicilioId, domiciliario);

        ArgumentCaptor<Pedido> actualizado = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).actualizar(actualizado.capture());
        assertEquals(EstadoPedido.ENTREGADO, actualizado.getValue().estado());
        ArgumentCaptor<DomainEvent> evento = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventos).publish(evento.capture());
        PedidoEntregadoEvent entregado = assertInstanceOf(PedidoEntregadoEvent.class, evento.getValue());
        assertEquals("pedidos.pedido-entregado", entregado.type());
        assertEquals(domicilioId, entregado.payload().get("domicilioId"));
        assertEquals(domiciliario, entregado.actorUserId());
    }

    @Test
    void noSeEntregaUnPedidoQueNoVaEnCamino() {
        Pedido pedido = aceptado(null);
        when(pedidos.bloquearPorId(pedido.id())).thenReturn(Optional.of(pedido));

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.entregarPorDomicilio(pedido.id(), UUID.randomUUID(), UUID.randomUUID()));

        assertEquals("INVALID_STATE", error.code());
        verify(eventos, never()).publish(any());
    }

    private static Pedido aceptado(Instant reserva) {
        Instant created = NOW.minusSeconds(10_000);
        return Pedido.nuevo(UUID.randomUUID(), UUID.randomUUID(), ModalidadEntrega.DOMICILIO, reserva,
                MedioPagoPedido.CUENTA_BANCARIA, 1_000L, 6_000, UUID.randomUUID(),
                List.of(new ItemPedido(UUID.randomUUID(), PRODUCTO, new BigDecimal("2.50"), 2_000)), created)
                .aceptar(created);
    }
}
