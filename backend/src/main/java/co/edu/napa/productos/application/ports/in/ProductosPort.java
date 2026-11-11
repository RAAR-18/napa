package co.edu.napa.productos.application.ports.in;

import co.edu.napa.productos.domain.model.EstadoProducto;
import co.edu.napa.productos.domain.model.Producto;
import co.edu.napa.productos.domain.model.UnidadProducto;
import co.edu.napa.shared.domain.DomainException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductosPort {
    Producto crear(UUID vendedorId, Datos datos);
    Pagina propios(UUID vendedorId, Consulta consulta);
    Pagina catalogo(UUID emprendimientoId, Consulta consulta);
    Producto actualizarInventario(UUID vendedorId, UUID productoId, BigDecimal cantidad);
    Producto consultarPropio(UUID vendedorId, UUID productoId);
    Producto editar(UUID vendedorId, UUID productoId, Edicion edicion);
    void eliminar(UUID vendedorId, UUID productoId);

    record Edicion(String nombre, BigDecimal precio, boolean cambiarDescripcion, String descripcion,
                   Long versionEsperada) {}

    record Datos(String nombre, BigDecimal precio, UnidadProducto unidad, String descripcion,
                 BigDecimal cantidadDisponible) {}

    record Consulta(int page, int size, String sort, EstadoProducto estado) {
        public Consulta {
            if (page < 0 || page > 1_000_000 || size < 1 || size > 100
                    || !List.of("nombre,asc", "nombre,desc", "precio,asc", "precio,desc").contains(sort)
                    || estado == EstadoProducto.ELIMINADO) {
                throw new DomainException("VALIDATION_ERROR", "Revisa la página, el tamaño, el orden o el estado solicitado.");
            }
        }
    }

    record ResumenEmprendimiento(UUID id, String nombre) {}
    record Pagina(List<Producto> content, int page, int size, long totalElements,
                  ResumenEmprendimiento emprendimiento) {}
}
