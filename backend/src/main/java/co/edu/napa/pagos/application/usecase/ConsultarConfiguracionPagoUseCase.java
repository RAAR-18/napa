package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.ConsultarConfiguracionPagoPort;
import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.domain.model.ConfiguracionPago;

import java.util.Objects;
import java.util.UUID;

public class ConsultarConfiguracionPagoUseCase implements ConsultarConfiguracionPagoPort {
    private final ConfiguracionPagoRepositoryPort configuraciones;

    public ConsultarConfiguracionPagoUseCase(ConfiguracionPagoRepositoryPort configuraciones) {
        this.configuraciones = Objects.requireNonNull(configuraciones, "configuraciones");
    }

    @Override
    public ConfiguracionPago consultar(UUID vendedorId) {
        Objects.requireNonNull(vendedorId, "vendedorId");
        return configuraciones.findByVendedorId(vendedorId)
                .orElseGet(() -> new ConfiguracionPago(vendedorId, false, null, null, null));
    }
}
