package co.edu.napa.emprendimientos.application.ports.in;

import co.edu.napa.shared.domain.geo.GeoPoint;

import java.util.Optional;
import java.util.UUID;

public interface ConsultarEmprendimientoParaPedidoPort {
    Optional<Referencia> consultar(UUID emprendimientoId);

    record Referencia(UUID id, UUID vendedorId, String nombre, GeoPoint puntoA) {}
}
