package co.edu.napa.pagos.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConfiguracionPago(
        UUID vendedorId,
        boolean aceptaEfectivo,
        CuentaBancaria cuentaBancaria,
        Instant createdAt,
        Instant updatedAt
) {
    public ConfiguracionPago {
        Objects.requireNonNull(vendedorId, "vendedorId");
    }
}
