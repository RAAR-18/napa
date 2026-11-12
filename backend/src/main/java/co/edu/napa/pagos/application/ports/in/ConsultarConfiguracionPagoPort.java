package co.edu.napa.pagos.application.ports.in;

import co.edu.napa.pagos.domain.model.ConfiguracionPago;

import java.util.UUID;

public interface ConsultarConfiguracionPagoPort {
    ConfiguracionPago consultar(UUID vendedorId);
}
