package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.ConfigurarPagoVendedorPort;
import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.domain.model.ConfiguracionPago;
import co.edu.napa.pagos.domain.model.CuentaBancaria;

import java.util.Objects;
import java.util.UUID;

public class ConfigurarPagoVendedorUseCase implements ConfigurarPagoVendedorPort {
    private final ConfiguracionPagoRepositoryPort configuraciones;

    public ConfigurarPagoVendedorUseCase(ConfiguracionPagoRepositoryPort configuraciones) {
        this.configuraciones = Objects.requireNonNull(configuraciones, "configuraciones");
    }

    @Override
    public ConfiguracionPago configurar(UUID vendedorId, boolean aceptaEfectivo, CuentaBancaria cuentaBancaria) {
        return configuraciones.save(new ConfiguracionPago(vendedorId, aceptaEfectivo, cuentaBancaria, null, null));
    }
}
