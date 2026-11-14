package co.edu.napa.pagos.application.ports.in;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * RF-43, para el módulo {@code domicilios}: él valida el domicilio, su estado y quién actúa (y lo bloquea antes);
 * aquí solo se lleva el registro del pago. La plataforma no mueve dinero.
 */
public interface PagoTarifaDomicilioPort {
    /** {@code CONFLICT} si ya existe un pago de tarifa para el domicilio. */
    PagoTarifa registrar(UUID domicilioId, UUID compradorId, UUID vendedorId, UUID domiciliarioId, long monto);

    /** {@code FEE_NOT_REGISTERED} si el vendedor no lo ha registrado; {@code ALREADY_CONFIRMED} si ya se confirmó. */
    PagoTarifa confirmar(UUID domicilioId);

    Optional<PagoTarifa> consultar(UUID domicilioId);

    /** Cuenta completa (sin enmascarar) del domiciliario, solo para que el vendedor le transfiera. */
    Optional<CuentaCobro> cuentaDelDomiciliario(UUID domiciliarioId);

    record CuentaCobro(String banco, String tipoCuenta, String numeroCuenta) {}

    record PagoTarifa(
            UUID pagoId,
            String estado,
            long monto,
            String medio,
            Instant registradoAt,
            Instant confirmadoAt
    ) {}
}
