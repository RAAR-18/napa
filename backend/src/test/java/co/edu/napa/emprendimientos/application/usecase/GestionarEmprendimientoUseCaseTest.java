package co.edu.napa.emprendimientos.application.usecase;

import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.emprendimientos.application.ports.in.GestionarEmprendimientoPort.Edicion;
import co.edu.napa.emprendimientos.application.ports.in.GestionarEmprendimientoPort.NuevaUbicacion;
import co.edu.napa.emprendimientos.application.ports.out.EmprendimientoRepositoryPort;
import co.edu.napa.emprendimientos.domain.model.CategoriaEmprendimiento;
import co.edu.napa.emprendimientos.domain.model.Emprendimiento;
import co.edu.napa.emprendimientos.domain.model.EstadoEmprendimiento;
import co.edu.napa.pedidos.application.ports.in.ConsultarPedidosSinCerrarPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.Ubicacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GestionarEmprendimientoUseCaseTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    private final EmprendimientoRepositoryPort businesses = mock(EmprendimientoRepositoryPort.class);
    private final UbicacionRepositoryPort locations = mock(UbicacionRepositoryPort.class);
    private final ConsultarPedidosSinCerrarPort orders = mock(ConsultarPedidosSinCerrarPort.class);
    private final GestionarEmprendimientoUseCase useCase = new GestionarEmprendimientoUseCase(
            businesses, locations, orders, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Emprendimiento business = new Emprendimiento(UUID.randomUUID(), SELLER, LOCATION, "Frutas Rosa",
            CategoriaEmprendimiento.FRUTAS_VERDURAS, "Frutas frescas", LocalTime.of(6, 0), LocalTime.of(18, 0),
            EstadoEmprendimiento.ACTIVO, CREATED, CREATED);
    private final Ubicacion location = new Ubicacion(LOCATION, 11.24, -74.21, "Calle 15", "Parque", CREATED, CREATED);

    @BeforeEach
    void setUp() {
        when(businesses.findActiveByUsuarioId(SELLER)).thenReturn(Optional.of(business));
        when(locations.findById(LOCATION)).thenReturn(Optional.of(location));
        when(businesses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(locations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void editsDescriptionAndScheduleKeepingNameAndCategory() {
        var result = useCase.editar(SELLER, RolUsuario.VENDEDOR_PUNTO_FIJO,
                new Edicion("  Frutas y jugos  ", LocalTime.of(7, 0), null, null));

        assertEquals("Frutas y jugos", result.emprendimiento().descripcion());
        assertEquals(LocalTime.of(7, 0), result.emprendimiento().horaApertura());
        assertEquals(LocalTime.of(18, 0), result.emprendimiento().horaCierre());
        assertEquals("Frutas Rosa", result.emprendimiento().nombre());
        assertEquals(NOW, result.emprendimiento().updatedAt());
        verify(locations, never()).save(any());
    }

    @Test
    void aStreetSellerMovesEvenWithOpenOrders() {
        when(orders.contarDeEmprendimiento(business.id())).thenReturn(2L);

        var result = useCase.editar(SELLER, RolUsuario.VENDEDOR_AMBULANTE,
                new Edicion(null, null, null, new NuevaUbicacion(11.25, -74.2, null, "Playa")));

        assertEquals(LOCATION, result.ubicacion().id());
        assertEquals(11.25, result.ubicacion().latitud());
        assertEquals("Playa", result.ubicacion().referencia());
        assertEquals(CREATED, result.ubicacion().createdAt());
    }

    @Test
    void aFixedSellerCannotMoveWithOpenOrders() {
        when(orders.contarDeEmprendimiento(business.id())).thenReturn(1L);

        DomainException error = assertThrows(DomainException.class, () -> useCase.editar(SELLER,
                RolUsuario.VENDEDOR_PUNTO_FIJO,
                new Edicion(null, null, null, new NuevaUbicacion(11.25, -74.2, "Calle 20", null))));

        assertEquals("HAS_PENDING_ORDERS", error.code());
        assertEquals(1L, error.details().get("pedidosPendientes"));
        verify(locations, never()).save(any());
        verify(businesses, never()).save(any());
    }

    @Test
    void aFixedSellerNeedsAnAddress() {
        DomainException error = assertThrows(DomainException.class, () -> useCase.editar(SELLER,
                RolUsuario.VENDEDOR_PUNTO_FIJO, new Edicion(null, null, null, new NuevaUbicacion(11.25, -74.2, " ", null))));

        assertEquals("VALIDATION_ERROR", error.code());
    }

    @Test
    void rejectsAnEmptyEditAndTheSameOpeningAndClosingTime() {
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> useCase.editar(SELLER, RolUsuario.VENDEDOR_AMBULANTE, new Edicion(null, null, null, null))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> useCase.editar(SELLER, RolUsuario.VENDEDOR_AMBULANTE,
                        new Edicion(null, LocalTime.of(18, 0), null, null))).code());
    }

    @Test
    void deletesLogicallyWhenThereAreNoOpenOrders() {
        useCase.eliminar(SELLER);

        ArgumentCaptor<Emprendimiento> saved = ArgumentCaptor.forClass(Emprendimiento.class);
        verify(businesses).save(saved.capture());
        assertEquals(EstadoEmprendimiento.ELIMINADO, saved.getValue().estado());
        assertEquals(business.id(), saved.getValue().id());
    }

    @Test
    void doesNotDeleteWithOpenOrdersIncludingReservations() {
        when(orders.contarDeEmprendimiento(business.id())).thenReturn(3L);

        DomainException error = assertThrows(DomainException.class, () -> useCase.eliminar(SELLER));

        assertEquals("HAS_PENDING_ORDERS", error.code());
        assertEquals(3L, error.details().get("pedidosPendientes"));
        verify(businesses, never()).save(any());
    }

    @Test
    void answersNotFoundWithoutAnActiveBusiness() {
        when(businesses.findActiveByUsuarioId(SELLER)).thenReturn(Optional.empty());

        DomainException error = assertThrows(DomainException.class,
                () -> useCase.consultarPropio(SELLER, RolUsuario.VENDEDOR_AMBULANTE));

        assertEquals("NOT_FOUND", error.code());
        assertEquals("Aún no tienes un emprendimiento activo.", error.getMessage());
    }
}
