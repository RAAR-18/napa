package co.edu.napa.pagos.application.ports.in;

import co.edu.napa.pagos.domain.model.ConfiguracionPago;
import co.edu.napa.pagos.domain.model.CuentaBancaria;

import java.util.UUID;

public interface ConfigurarPagoVendedorPort {
    ConfiguracionPago configurar(UUID vendedorId, boolean aceptaEfectivo, CuentaBancaria cuentaBancaria);
}
