package co.edu.napa.pedidos.domain.model;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoTest {
    private static final Instant CREATED_AT = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant RESPONDED_AT = CREATED_AT.plusSeconds(60);

    @Test
    void acceptsOnlyPendingOrders() {
        Pedido accepted = pending().aceptar(RESPONDED_AT);

        assertEquals(EstadoPedido.ACEPTADO, accepted.estado());
        assertEquals(RESPONDED_AT, accepted.updatedAt());
        assertEquals(EstadoPedido.PENDIENTE, pending().estado());
        assertInvalidState(accepted::aceptar);
    }

    @Test
    void rejectsOnlyPendingOrders() {
        Pedido rejected = pending().rechazar(RESPONDED_AT);

        assertEquals(EstadoPedido.RECHAZADO, rejected.estado());
        assertEquals(RESPONDED_AT, rejected.updatedAt());
        assertInvalidState(rejected::rechazar);
    }

    @Test
    void cancelsAnAcceptedHomeDeliveryOrderWhenItsDeliveryIsCancelled() {
        Pedido delivery = Pedido.nuevo(UUID.randomUUID(), UUID.randomUUID(), ModalidadEntrega.DOMICILIO, null,
                MedioPagoPedido.CUENTA_BANCARIA, 1_000L, 4_500, UUID.randomUUID(),
                List.of(new ItemPedido(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 3500)), CREATED_AT);

        assertInvalidState(delivery::cancelarPorDomicilio);
        Pedido cancelled = delivery.aceptar(RESPONDED_AT).cancelarPorDomicilio(RESPONDED_AT.plusSeconds(7_200));

        assertEquals(EstadoPedido.CANCELADO, cancelled.estado());
        assertInvalidState(cancelled::cancelarPorDomicilio);
        assertInvalidState(pending().aceptar(RESPONDED_AT)::cancelarPorDomicilio);
    }

    private static void assertInvalidState(java.util.function.Function<Instant, Pedido> transition) {
        DomainException exception = assertThrows(DomainException.class,
                () -> transition.apply(RESPONDED_AT.plusSeconds(1)));
        assertEquals("INVALID_STATE", exception.code());
    }

    private static Pedido pending() {
        return Pedido.nuevo(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModalidadEntrega.RECOGIDA,
                null,
                MedioPagoPedido.EFECTIVO,
                null,
                3500,
                null,
                List.of(new ItemPedido(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 3500)),
                CREATED_AT
        );
    }
}
