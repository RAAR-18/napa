package co.edu.napa.productos.application.usecase;

import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoPort;
import co.edu.napa.productos.application.ports.in.ProductosPort;
import co.edu.napa.productos.application.ports.out.ProductoRepositoryPort;
import co.edu.napa.productos.application.ports.out.PedidosPendientesProductoPort;
import co.edu.napa.productos.domain.model.Producto;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ProductosUseCase implements ProductosPort {
    private final ProductoRepositoryPort productos;
    private final ConsultarEmprendimientoPort emprendimientos;
    private final Clock clock;
    private final PedidosPendientesProductoPort pedidosPendientes;

    public ProductosUseCase(ProductoRepositoryPort productos, ConsultarEmprendimientoPort emprendimientos, Clock clock,
                           PedidosPendientesProductoPort pedidosPendientes) {
        this.productos = productos;
        this.emprendimientos = emprendimientos;
        this.clock = clock;
        this.pedidosPendientes = pedidosPendientes;
    }

    @Transactional
    public Producto crear(UUID vendedorId, Datos datos) {
        var emprendimiento = emprendimientos.activoDelVendedor(vendedorId)
                .orElseThrow(() -> new DomainException("BUSINESS_RULE", "Primero crea tu emprendimiento."));
        return productos.insertar(Producto.crear(emprendimiento.id(), datos.nombre(), datos.precio(),
                datos.unidad(), datos.descripcion(), datos.cantidadDisponible(), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Pagina propios(UUID vendedorId, Consulta consulta) {
        var emprendimiento = emprendimientos.activoDelVendedor(vendedorId);
        if (emprendimiento.isEmpty()) return new Pagina(List.of(), consulta.page(), consulta.size(), 0, null);
        return listar(emprendimiento.get(), consulta);
    }

    @Transactional(readOnly = true)
    public Pagina catalogo(UUID emprendimientoId, Consulta consulta) {
        var emprendimiento = emprendimientos.activoPorId(emprendimientoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El emprendimiento no está disponible."));
        return listar(emprendimiento, consulta);
    }

    @Transactional
    public Producto actualizarInventario(UUID vendedorId, UUID productoId, BigDecimal cantidad) {
        Producto producto = productos.bloquear(productoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No se encontró el producto."));
        verificarPropietario(vendedorId, producto);
        return productos.actualizarInventario(producto.conInventario(cantidad, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Producto consultarPropio(UUID vendedorId, UUID productoId) {
        Producto producto = buscar(productoId);
        verificarPropietario(vendedorId, producto);
        producto.requirePublished();
        return producto;
    }

    @Transactional
    public Producto editar(UUID vendedorId, UUID productoId, Edicion edicion) {
        Producto producto = buscar(productoId);
        verificarPropietario(vendedorId, producto);
        producto.requirePublished();
        if (edicion.versionEsperada() != null && edicion.versionEsperada() != producto.version()) {
            throw new DomainException("CONFLICT", "El producto cambió mientras editabas. Vuelve a cargar sus datos.");
        }
        return productos.actualizarDatos(producto.editar(edicion.nombre(), edicion.precio(),
                edicion.cambiarDescripcion(), edicion.descripcion(), Instant.now(clock)));
    }

    @Transactional
    public void eliminar(UUID vendedorId, UUID productoId) {
        Producto producto = productos.bloquear(productoId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No se encontró el producto."));
        verificarPropietario(vendedorId, producto);
        producto.requirePublished();
        long pendientes = pedidosPendientes.contar(productoId);
        if (pendientes < 0) {
            throw new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", "No se pudieron comprobar los pedidos del producto.");
        }
        if (pendientes > 0) {
            throw new DomainException("HAS_PENDING_ORDERS", "Este producto tiene pedidos pendientes.",
                    Map.of("pedidosPendientes", pendientes));
        }
        productos.eliminar(producto.eliminar(Instant.now(clock)));
    }

    private Producto buscar(UUID id) {
        return productos.buscar(id).orElseThrow(() -> new DomainException("NOT_FOUND", "No se encontró el producto."));
    }

    private void verificarPropietario(UUID vendedorId, Producto producto) {
        var emprendimiento = emprendimientos.activoPorId(producto.emprendimientoId())
                .orElseThrow(() -> new DomainException("INVALID_STATE", "El emprendimiento no está activo."));
        if (!emprendimiento.usuarioId().equals(vendedorId)) {
            throw new DomainException("FORBIDDEN", "Solo puedes modificar tus propios productos.");
        }
    }

    private Pagina listar(ConsultarEmprendimientoPort.Referencia emprendimiento, Consulta consulta) {
        var result = productos.listar(emprendimiento.id(), consulta);
        return new Pagina(result.content(), consulta.page(), consulta.size(), result.totalElements(),
                new ResumenEmprendimiento(emprendimiento.id(), emprendimiento.nombre()));
    }
}
