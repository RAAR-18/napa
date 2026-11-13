package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConfirmarPagoPedidoPort;
import co.edu.napa.pedidos.application.ports.out.PedidoRepositoryPort;
import co.edu.napa.pedidos.domain.model.EstadoPedido;
import co.edu.napa.pedidos.domain.model.ItemPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmarPagoDomicilioUseCaseTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();
    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    @Test
    void confirmsOnlyAnAcceptedHomeDeliveryOrderOwnedByTheSeller() {
        PedidoRepositoryPort orders = mock(PedidoRepositoryPort.class);
        ConfirmarPagoPedidoPort payments = mock(ConfirmarPagoPedidoPort.class);
        Pedido order = order(EstadoPedido.ACEPTADO, ModalidadEntrega.DOMICILIO);
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(order));
        ConsultarEmprendimientoParaPedidoPort businesses = id -> Optional.of(
                new ConsultarEmprendimientoParaPedidoPort.Referencia(BUSINESS_ID, SELLER_ID, "Prueba", null));
        when(payments.confirmar(ORDER_ID, BUYER_ID, SELLER_ID, "DOMICILIO"))
                .thenReturn(new ConfirmarPagoPedidoPort.PagoConfirmado(
                        UUID.randomUUID(), "CONFIRMADO", "CUENTA_BANCARIA", order.total(), NOW));

        var result = new ConfirmarPagoDomicilioUseCase(orders, businesses, payments)
                .confirmar(ORDER_ID, SELLER_ID);

        assertEquals(ORDER_ID, result.pedidoId());
        assertEquals("CONFIRMADO", result.pago().estado());
        assertEquals(order.total(), result.pago().monto());
        verify(payments).confirmar(ORDER_ID, BUYER_ID, SELLER_ID, "DOMICILIO");
    }

    @Test
    void rejectsUnacceptedOrdersBeforeConfirmingPayment() {
        PedidoRepositoryPort orders = mock(PedidoRepositoryPort.class);
        ConfirmarPagoPedidoPort payments = mock(ConfirmarPagoPedidoPort.class);
        when(orders.bloquearPorId(ORDER_ID)).thenReturn(Optional.of(
                order(EstadoPedido.PENDIENTE, ModalidadEntrega.DOMICILIO)));
        ConsultarEmprendimientoParaPedidoPort businesses = id -> Optional.of(
                new ConsultarEmprendimientoParaPedidoPort.Referencia(BUSINESS_ID, SELLER_ID, "Prueba", null));

        DomainException error = assertThrows(DomainException.class,
                () -> new ConfirmarPagoDomicilioUseCase(orders, businesses, payments)
                        .confirmar(ORDER_ID, SELLER_ID));

        assertEquals("INVALID_STATE", error.code());
        assertEquals("PENDIENTE", error.details().get("estadoActual"));
        verify(payments, never()).confirmar(ORDER_ID, BUYER_ID, SELLER_ID, "DOMICILIO");
    }

    private static Pedido order(EstadoPedido state, ModalidadEntrega modality) {
        return new Pedido(ORDER_ID, BUYER_ID, BUSINESS_ID, modality, state, null, MedioPagoPedido.CUENTA_BANCARIA,
                2_500L, 3_500L, UUID.randomUUID(),
                List.of(new ItemPedido(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 1_000)),
                NOW, NOW);
    }
}
