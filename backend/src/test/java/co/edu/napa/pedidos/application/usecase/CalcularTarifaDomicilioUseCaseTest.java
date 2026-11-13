package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.pedidos.application.ports.out.DistanciaLineaRectaPort;
import co.edu.napa.pedidos.application.ports.out.TarifaDomicilioConfiguracionPort;
import co.edu.napa.pedidos.domain.model.ItemPedido;
import co.edu.napa.pedidos.domain.model.MedioPagoPedido;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.pedidos.domain.model.Pedido;
import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.RouteResult;
import co.edu.napa.tarifas.application.ports.in.ConsultarTarifaMinimaPort;
import co.edu.napa.tarifas.domain.model.TarifaMinima;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalcularTarifaDomicilioUseCaseTest {
    private static final GeoPoint ORIGEN = new GeoPoint(4.6, -74.1);
    private static final GeoPoint DESTINO = new GeoPoint(4.7, -74.0);
    private static final TarifaDomicilioConfiguracionPort CONFIGURACION = new TarifaDomicilioConfiguracionPort() {
        @Override public long base() { return 3_050; }
        @Override public long valorPorKm() { return 500; }
        @Override public long valorPorMin() { return 200; }
        @Override public BigDecimal factorLineaRecta() { return new BigDecimal("1.3"); }
    };
    private static final ConsultarTarifaMinimaPort TARIFA_MINIMA =
            () -> new TarifaMinima(3_050, null);

    @Test
    void calculatesFromRouteWhenRoutingSucceeds() {
        RoutingPort routes = (origin, destination) ->
                new RouteResult(2_000, Duration.ofMinutes(10), "encoded");
        DistanciaLineaRectaPort distances = (origin, destination) -> {
            throw new AssertionError("The fallback must not be used when a route is available");
        };

        long amount = useCase(routes, distances).calcular(ORIGEN, DESTINO);

        assertEquals(6_100, amount);
    }

    @Test
    void usesStraightLineFallbackOnlyWhenRoutingIsUnavailable() {
        RoutingPort routes = (origin, destination) -> {
            throw new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", "Route unavailable");
        };
        AtomicBoolean fallbackUsed = new AtomicBoolean();
        DistanciaLineaRectaPort distances = (origin, destination) -> {
            fallbackUsed.set(true);
            return BigDecimal.valueOf(2_000);
        };

        long amount = useCase(routes, distances).calcular(ORIGEN, DESTINO);

        assertEquals(4_400, amount);
        assertTrue(fallbackUsed.get());
    }

    @Test
    void doesNotHideOtherDomainErrors() {
        RoutingPort routes = (origin, destination) -> {
            throw new DomainException("VALIDATION_ERROR", "Invalid route request");
        };
        DistanciaLineaRectaPort distances = (origin, destination) -> BigDecimal.ZERO;

        DomainException error = assertThrows(
                DomainException.class,
                () -> useCase(routes, distances).calcular(ORIGEN, DESTINO)
        );

        assertEquals("VALIDATION_ERROR", error.code());
    }

    @Test
    void updatedMinimumAppliesToNewOrdersWithoutChangingExistingOrders() {
        AtomicLong minimum = new AtomicLong(3_050);
        ConsultarTarifaMinimaPort currentMinimum = () -> new TarifaMinima(minimum.get(), null);
        RoutingPort routes = (origin, destination) -> new RouteResult(2_000, Duration.ofMinutes(10), "encoded");
        var calculator = new CalcularTarifaDomicilioUseCase(routes, (origin, destination) -> BigDecimal.ZERO,
                currentMinimum, CONFIGURACION);
        UUID buyerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-11T12:00:00Z");
        var item = new ItemPedido(UUID.randomUUID(), productId, BigDecimal.ONE, 3_500);

        long firstFee = calculator.calcular(ORIGEN, DESTINO);
        Pedido firstOrder = Pedido.nuevo(buyerId, businessId, ModalidadEntrega.DOMICILIO, null,
                MedioPagoPedido.CUENTA_BANCARIA, firstFee, 3_500 + firstFee, locationId, List.of(item), now);

        minimum.set(8_000);
        long secondFee = calculator.calcular(ORIGEN, DESTINO);
        Pedido secondOrder = Pedido.nuevo(buyerId, businessId, ModalidadEntrega.DOMICILIO, null,
                MedioPagoPedido.CUENTA_BANCARIA, secondFee, 3_500 + secondFee, locationId,
                List.of(item), now.plusSeconds(1));

        assertEquals(6_100, firstOrder.tarifaDomicilio());
        assertEquals(9_600, firstOrder.total());
        assertEquals(8_000, secondOrder.tarifaDomicilio());
        assertEquals(11_500, secondOrder.total());
    }

    private static CalcularTarifaDomicilioUseCase useCase(
            RoutingPort routes,
            DistanciaLineaRectaPort distances
    ) {
        return new CalcularTarifaDomicilioUseCase(routes, distances, TARIFA_MINIMA, CONFIGURACION);
    }
}
