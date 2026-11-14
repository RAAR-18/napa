package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort.MediosDisponibles;
import co.edu.napa.pedidos.application.ports.in.CalcularTarifaDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.ItemSolicitud;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.Solicitud;
import co.edu.napa.pedidos.application.ports.in.CotizarPedidoPort.UbicacionEntrega;
import co.edu.napa.pedidos.domain.model.ModalidadEntrega;
import co.edu.napa.productos.application.ports.in.ConsultarDisponibilidadPort;
import co.edu.napa.productos.application.ports.in.ConsultarDisponibilidadPort.ProductoDisponible;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CotizarPedidoUseCaseTest {
    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final UbicacionEntrega DESTINATION =
            new UbicacionEntrega(4.7, -74.0, "Calle de prueba", null, "Centro");

    @Test
    void homeDeliveryRequiresTheNeighborhood() {
        var useCase = useCase(true, true, List.of("EFECTIVO", "CUENTA_BANCARIA"),
                List.of(product("DISPONIBLE", "5")));

        DomainException error = assertThrows(DomainException.class, () -> useCase.cotizar(request(
                ModalidadEntrega.DOMICILIO, "1.5", null, new UbicacionEntrega(4.7, -74.0, "Calle de prueba", null))));

        assertEquals("VALIDATION_ERROR", error.code());
        assertEquals("Indica el barrio de entrega.", error.getMessage());
    }

    @Test
    void quotesFixedPointHomeDeliveryWithBankTransferAndDeliveryFee() {
        var result = useCase(true, true, List.of("EFECTIVO", "CUENTA_BANCARIA"),
                List.of(product("DISPONIBLE", "5")))
                .cotizar(request(ModalidadEntrega.DOMICILIO, "1.5", null, DESTINATION));

        assertEquals(5_250, result.subtotal());
        assertEquals(4_200, result.tarifaDomicilio());
        assertEquals(9_450, result.total());
        assertEquals(List.of(ModalidadEntrega.RECOGIDA, ModalidadEntrega.DOMICILIO),
                result.modalidadesDisponibles());
        assertEquals(List.of("CUENTA_BANCARIA"), result.mediosPagoDisponibles());
        assertEquals(new CotizarPedidoPort.PagoDomicilio("CUENTA_BANCARIA", "AL_SER_ACEPTADO", 9_450),
                result.pagoDomicilio());
    }

    @Test
    void immediateOrderRejectsExhaustedProductButFutureReservationAllowsIt() {
        var useCase = useCase(false, false, List.of("EFECTIVO"),
                List.of(product("AGOTADO", "0")));
        var immediate = request(ModalidadEntrega.ENTREGA_DIRECTA, "1", null, DESTINATION);

        DomainException error = assertThrows(DomainException.class, () -> useCase.cotizar(immediate));
        assertEquals("PRODUCT_UNAVAILABLE", error.code());

        var reservation = request(ModalidadEntrega.ENTREGA_DIRECTA, "1", NOW.plusSeconds(60), DESTINATION);
        var quote = useCase.cotizar(reservation);
        assertEquals(3_500, quote.total());
        assertEquals("AGOTADO", quote.items().getFirst().estadoProducto());
    }

    @Test
    void immediateOrderRejectsRequestedQuantityAboveCurrentStock() {
        var useCase = useCase(false, false, List.of("EFECTIVO"),
                List.of(product("DISPONIBLE", "1")));

        DomainException error = assertThrows(DomainException.class, () -> useCase.cotizar(
                request(ModalidadEntrega.ENTREGA_DIRECTA, "1.01", null, DESTINATION)));

        assertEquals("OUT_OF_STOCK", error.code());
        assertEquals(new BigDecimal("1"), error.details().get("disponible"));
    }

    @Test
    void rejectsPickupLocationAndMissingFutureReservation() {
        var useCase = useCase(true, false, List.of("EFECTIVO"),
                List.of(product("DISPONIBLE", "5")));

        DomainException locationError = assertThrows(DomainException.class, () -> useCase.cotizar(
                request(ModalidadEntrega.RECOGIDA, "1", null, DESTINATION)));
        assertEquals("VALIDATION_ERROR", locationError.code());
        assertNull(locationError.details().get("productoId"));

        DomainException reservationError = assertThrows(DomainException.class, () -> useCase.cotizar(
                request(ModalidadEntrega.RECOGIDA, "1", NOW, null)));
        assertEquals("VALIDATION_ERROR", reservationError.code());
    }

    private static CotizarPedidoUseCase useCase(
            boolean puntoFijo,
            boolean domicilioDisponible,
            List<String> pagos,
            List<ProductoDisponible> disponibles
    ) {
        ConsultarEmprendimientoParaPedidoPort emprendimientos = id -> Optional.of(
                new ConsultarEmprendimientoParaPedidoPort.Referencia(
                        BUSINESS_ID, SELLER_ID, "Emprendimiento de prueba", new GeoPoint(4.6, -74.1)));
        ConsultarDisponibilidadPort productos = (id, ids) -> disponibles;
        ConsultarMediosPagoPort mediosPago = id ->
                new MediosDisponibles(pagos, domicilioDisponible, puntoFijo);
        CalcularTarifaDomicilioPort tarifas = (origen, destino) -> 4_200;
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new CotizarPedidoUseCase(emprendimientos, productos, mediosPago, tarifas, clock);
    }

    private static ProductoDisponible product(String state, String stock) {
        return new ProductoDisponible(PRODUCT_ID, "Mango", "KG", 3_500,
                new BigDecimal(stock), state);
    }

    private static Solicitud request(
            ModalidadEntrega modalidad,
            String cantidad,
            Instant reserva,
            UbicacionEntrega ubicacion
    ) {
        return new Solicitud(BUSINESS_ID, modalidad,
                List.of(new ItemSolicitud(PRODUCT_ID, new BigDecimal(cantidad))), ubicacion, reserva);
    }
}
