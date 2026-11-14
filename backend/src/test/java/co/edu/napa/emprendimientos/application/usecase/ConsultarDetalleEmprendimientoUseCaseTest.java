package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort;
import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort.PerfilPublico;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.TipoVendedor;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort.MediosDisponibles;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarDetalleEmprendimientoUseCaseTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2026-09-01T15:00:00Z");
    // 10:00 en Bogotá.
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    private final EmprendimientoRepositoryPort businesses = mock(EmprendimientoRepositoryPort.class);
    private final UbicacionRepositoryPort locations = mock(UbicacionRepositoryPort.class);
    private final ConsultarPerfilPublicoUsuarioPort users = mock(ConsultarPerfilPublicoUsuarioPort.class);
    private final ConsultarMediosPagoPort payments = mock(ConsultarMediosPagoPort.class);
    private final ConsultarDetalleEmprendimientoUseCase useCase = new ConsultarDetalleEmprendimientoUseCase(
            businesses, locations, users, payments, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Emprendimiento business = new Emprendimiento(UUID.randomUUID(), SELLER, LOCATION, "Frutas Rosa",
            CategoriaEmprendimiento.FRUTAS_VERDURAS, "Frutas frescas", LocalTime.of(6, 0), LocalTime.of(18, 0),
            EstadoEmprendimiento.ACTIVO, CREATED, CREATED);

    @BeforeEach
    void setUp() {
        when(businesses.findActiveById(business.id())).thenReturn(Optional.of(business));
        when(locations.findById(LOCATION)).thenReturn(Optional.of(
                new Ubicacion(LOCATION, 11.24, -74.21, "Calle 15", "Parque", CREATED, CREATED)));
    }

    @Test
    void aFixedSellerWithBankAccountOffersPickupAndHomeDelivery() {
        profile("VENDEDOR_PUNTO_FIJO");
        when(payments.consultar(SELLER)).thenReturn(new MediosDisponibles(List.of("EFECTIVO"), true, true));

        var detail = useCase.consultar(business.id());

        assertEquals(TipoVendedor.PUNTO_FIJO, detail.tipoVendedor());
        assertTrue(detail.abierto());
        assertTrue(detail.ofreceDomicilio());
        assertEquals(List.of("RECOGIDA", "DOMICILIO"), detail.modalidadesEntrega());
        assertEquals("Calle 15", detail.ubicacion().direccion());
        assertEquals("06:00", detail.horaApertura());
    }

    @Test
    void aStreetSellerOnlyDeliversDirectly() {
        profile("VENDEDOR_AMBULANTE");

        var detail = useCase.consultar(business.id());

        assertEquals(TipoVendedor.AMBULANTE, detail.tipoVendedor());
        assertEquals(List.of("ENTREGA_DIRECTA"), detail.modalidadesEntrega());
        assertFalse(detail.ofreceDomicilio());
    }

    @Test
    void withoutPaymentsTheDetailIsShownWithoutHomeDelivery() {
        profile("VENDEDOR_PUNTO_FIJO");
        when(payments.consultar(SELLER)).thenThrow(new IllegalStateException("caído"));

        var detail = useCase.consultar(business.id());

        assertEquals(List.of("RECOGIDA"), detail.modalidadesEntrega());
    }

    @Test
    void aDeletedOrMissingBusinessIsNoLongerAvailable() {
        UUID other = UUID.randomUUID();
        when(businesses.findActiveById(other)).thenReturn(Optional.empty());

        DomainException error = assertThrows(DomainException.class, () -> useCase.consultar(other));

        assertEquals("NOT_FOUND", error.code());
        assertEquals("El emprendimiento ya no está disponible.", error.getMessage());
        assertEquals("NOT_FOUND", assertThrows(DomainException.class, () -> useCase.vendedor(other)).code());
    }

    @Test
    void theSellerCardOnlyHasPublicData() {
        when(users.consultarPerfil(SELLER)).thenReturn(Optional.of(
                new PerfilPublico("Rosa Martínez", null, "VENDEDOR_PUNTO_FIJO", CREATED)));

        var seller = useCase.vendedor(business.id());

        assertEquals("Rosa Martínez", seller.nombre());
        assertEquals(null, seller.fotoUrl());
        assertEquals(TipoVendedor.PUNTO_FIJO, seller.tipoVendedor());
        assertEquals(LocalDate.of(2026, 9, 1), seller.miembroDesde());
    }

    private void profile(String role) {
        when(users.consultarPerfil(SELLER)).thenReturn(Optional.of(
                new PerfilPublico("Rosa Martínez", "https://cdn/rosa.jpg", role, CREATED)));
    }
}
