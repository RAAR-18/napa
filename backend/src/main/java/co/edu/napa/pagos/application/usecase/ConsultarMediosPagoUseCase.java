package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarMediosPagoPort.MediosDisponibles;
import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.ConsultarTipoVendedorPort;
import co.edu.napa.pagos.domain.model.ConfiguracionPago;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.MediosPago;
import co.edu.napa.pagos.domain.model.TipoVendedor;
import co.edu.napa.shared.domain.DomainException;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

public class ConsultarMediosPagoUseCase implements ConsultarMediosPagoPort {
    private final ConfiguracionPagoRepositoryPort configuraciones;
    private final ConsultarTipoVendedorPort vendedores;

    public ConsultarMediosPagoUseCase(
            ConfiguracionPagoRepositoryPort configuraciones,
            ConsultarTipoVendedorPort vendedores
    ) {
        this.configuraciones = Objects.requireNonNull(configuraciones, "configuraciones");
        this.vendedores = Objects.requireNonNull(vendedores, "vendedores");
    }

    @Override
    public MediosDisponibles consultar(UUID vendedorId) {
        Objects.requireNonNull(vendedorId, "vendedorId");
        ConfiguracionPago configuracion = configuraciones.findByVendedorId(vendedorId)
                .orElseGet(() -> new ConfiguracionPago(vendedorId, false, null, null, null));
        TipoVendedor tipo = vendedores.consultar(vendedorId)
                .orElseThrow(() -> new DomainException("NOT_FOUND", "No se encontró el vendedor."));

        ArrayList<MedioPago> medios = new ArrayList<>();
        if (configuracion.aceptaEfectivo()) {
            medios.add(MedioPago.EFECTIVO);
        }
        boolean tieneCuenta = configuracion.cuentaBancaria() != null;
        if (tieneCuenta) {
            medios.add(MedioPago.CUENTA_BANCARIA);
        }
        boolean puntoFijo = tipo == TipoVendedor.PUNTO_FIJO;
        return new MediosDisponibles(medios.stream().map(Enum::name).toList(),
                puntoFijo && tieneCuenta, puntoFijo);
    }
}
