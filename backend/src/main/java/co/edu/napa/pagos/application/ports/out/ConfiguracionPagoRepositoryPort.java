package co.edu.napa.pagos.application.ports.out;

import co.edu.napa.pagos.domain.model.ConfiguracionPago;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracionPagoRepositoryPort {
    ConfiguracionPago save(ConfiguracionPago configuracion);
    Optional<ConfiguracionPago> findByVendedorId(UUID vendedorId);
}
