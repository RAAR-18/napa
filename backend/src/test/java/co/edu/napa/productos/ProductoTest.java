package co.edu.napa.productos;

import co.edu.napa.productos.domain.model.EstadoProducto;
import co.edu.napa.productos.domain.model.Producto;
import co.edu.napa.productos.domain.model.UnidadProducto;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductoTest {
    @Test
    void normalizesNamesAndPreservesDecimalQuantityWithoutRounding() {
        Producto value = create("  Mango  ", "3500", "12.50");
        assertEquals("Mango", value.nombre());
        assertEquals(new BigDecimal("12.50"), value.cantidadDisponible());
        assertEquals(EstadoProducto.DISPONIBLE, value.estado());
        Producto exhausted = value.conInventario(BigDecimal.ZERO, Instant.now());
        assertEquals(EstadoProducto.AGOTADO, exhausted.estado());
        assertEquals(EstadoProducto.DISPONIBLE,
                exhausted.conInventario(new BigDecimal("0.25"), Instant.now()).estado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "100000000", "1.234"})
    void rejectsInvalidInitialQuantities(String quantity) {
        DomainException error = assertThrows(DomainException.class, () -> create("Mango", "3500", quantity));
        assertEquals("VALIDATION_ERROR", error.code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "3500.50", "9007199254740992", "9999999999999999999999999"})
    void rejectsInvalidPricesInsteadOfTruncatingDecimals(String price) {
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> create("Mango", price, "1")).code());
    }

    @Test
    void rejectsEmptyNamesAndDeletedInventoryChanges() {
        assertThrows(DomainException.class, () -> create("  ", "3500", "1"));
        Producto value = create("Mango", "3500", "1");
        Producto deleted = new Producto(value.id(), value.emprendimientoId(), value.nombre(), value.precio(),
                value.unidad(), null, value.cantidadDisponible(), EstadoProducto.ELIMINADO,
                value.createdAt(), value.updatedAt(), value.version());
        assertEquals("INVALID_STATE", assertThrows(DomainException.class,
                () -> deleted.conInventario(BigDecimal.ONE, Instant.now())).code());
    }

    @Test
    void editingPreservesUnitInventoryAndAvailabilityAndCanClearDescription() {
        Producto original = create("Mango", "3500", "2.5").conInventario(BigDecimal.ZERO, Instant.now());
        Producto edited = original.editar("Mango dulce", new BigDecimal("4500"), true, null, Instant.now());
        assertEquals("Mango dulce", edited.nombre()); assertEquals(4500, edited.precio());
        assertEquals(original.unidad(), edited.unidad()); assertEquals(original.cantidadDisponible(), edited.cantidadDisponible());
        assertEquals(EstadoProducto.AGOTADO, edited.estado()); assertNull(edited.descripcion());
        assertEquals("Mango", original.nombre()); assertEquals(3500, original.precio());
    }

    @Test
    void deletedProductsCannotBeEditedOrDeletedAgain() {
        Producto deleted = create("Mango", "3500", "1").eliminar(Instant.now());
        assertEquals(EstadoProducto.ELIMINADO, deleted.estado());
        assertEquals("INVALID_STATE", assertThrows(DomainException.class,
                () -> deleted.editar("Nuevo", null, false, null, Instant.now())).code());
        assertEquals("INVALID_STATE", assertThrows(DomainException.class,
                () -> deleted.eliminar(Instant.now())).code());
    }

    private Producto create(String name, String price, String quantity) {
        return Producto.crear(UUID.randomUUID(), name, new BigDecimal(price), UnidadProducto.KG,
                null, new BigDecimal(quantity), Instant.now());
    }
}
