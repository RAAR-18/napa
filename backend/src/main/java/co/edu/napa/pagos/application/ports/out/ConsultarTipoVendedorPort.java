package co.edu.napa.pagos.application.ports.out;

import co.edu.napa.pagos.domain.model.TipoVendedor;

import java.util.Optional;
import java.util.UUID;

public interface ConsultarTipoVendedorPort {
    Optional<TipoVendedor> consultar(UUID vendedorId);
}
