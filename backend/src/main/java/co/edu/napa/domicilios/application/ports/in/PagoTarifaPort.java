package co.edu.napa.domicilios.application.ports.in;

import java.time.Instant;
import java.util.UUID;

/** RF-43: el vendedor registra que transfirió la tarifa y el domiciliario confirma que la recibió. */
public interface PagoTarifaPort {
    EstadoPagoTarifa registrar(UUID vendedorId, UUID domicilioId);

    EstadoPagoTarifa confirmar(UUID domiciliarioId, UUID domicilioId);

    EstadoPagoTarifa consultarComoVendedor(UUID vendedorId, UUID domicilioId);

    EstadoPagoTarifa consultarComoDomiciliario(UUID domiciliarioId, UUID domicilioId);

    /**
     * {@code estado}: {@code SIN_REGISTRAR}, {@code REGISTRADO} o {@code CONFIRMADO}. {@code cuentaDomiciliario} solo
     * llega al vendedor y con el domicilio entregado; es nula si el domiciliario no ha registrado su cuenta.
     */
    record EstadoPagoTarifa(
            UUID domicilioId,
            UUID pagoId,
            String estado,
            long monto,
            String medio,
            Instant registradoAt,
            Instant confirmadoAt,
            CuentaDomiciliario cuentaDomiciliario
    ) {}

    record CuentaDomiciliario(String banco, String tipoCuenta, String numeroCuenta) {}
}
