package co.edu.napa.pagos.domain.model;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagoConfirmacionTest {
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    @Test
    void confirmsPendingOrderPaymentAndRejectsDuplicateConfirmation() {
        Pago pendiente = Pago.pedidoPendiente(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                12_500, MedioPago.CUENTA_BANCARIA, NOW);

        Pago confirmado = pendiente.confirmar(NOW.plusSeconds(5));

        assertEquals(EstadoPago.CONFIRMADO, confirmado.estado());
        assertEquals(NOW.plusSeconds(5), confirmado.confirmadoAt());
        assertEquals(pendiente.medio(), confirmado.medio());
        assertEquals(pendiente.monto(), confirmado.monto());
        DomainException error = assertThrows(DomainException.class,
                () -> confirmado.confirmar(NOW.plusSeconds(6)));
        assertEquals("ALREADY_CONFIRMED", error.code());
    }

    @Test
    void doesNotConfirmTariffPaymentsThroughTheOrderPaymentTransition() {
        Pago tarifa = new Pago(UUID.randomUUID(), TipoPago.TARIFA_DOMICILIO, 2_500,
                MedioPago.CUENTA_BANCARIA, EstadoPago.REGISTRADO, null, null, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW, NOW);

        DomainException error = assertThrows(DomainException.class, () -> tarifa.confirmar(NOW));

        assertEquals("BUSINESS_RULE", error.code());
    }
}
