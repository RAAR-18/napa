package co.edu.napa.productos.application.ports.out;

import co.edu.napa.productos.application.ports.in.ProductosPort.Consulta;
import co.edu.napa.productos.domain.model.Producto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoRepositoryPort {
    Producto insertar(Producto producto);
    Optional<Producto> bloquear(UUID id);
    Optional<Producto> buscar(UUID id);
    Producto actualizarInventario(Producto producto);
    Producto actualizarDatos(Producto producto);
    void eliminar(Producto producto);
    Resultado listar(UUID emprendimientoId, Consulta consulta);
    List<Producto> consultarDisponibilidad(UUID emprendimientoId, List<UUID> productoIds);
    List<UUID> bloquearDisponibles(UUID emprendimientoId, List<UUID> productoIds);

    record Resultado(List<Producto> content, long totalElements) {}
}
