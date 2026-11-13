package co.edu.napa.pagos.application.usecase;

import co.edu.napa.pagos.application.ports.in.RegistrarPagoPedidoPendientePort;
import co.edu.napa.pagos.application.ports.in.RegistrarPagoPedidoPendientePort.PagoRegistrado;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.domain.model.MedioPago;
import co.edu.napa.pagos.domain.model.Pago;
import co.edu.napa.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class RegistrarPagoPedidoPendienteUseCase implements RegistrarPagoPedidoPendientePort {
    private final PagoRepositoryPort pagos;
    private final Clock clock;

    public RegistrarPagoPedidoPendienteUseCase(PagoRepositoryPort pagos, Clock clock) {
        this.pagos = Objects.requireNonNull(pagos, "pagos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PagoRegistrado registrar(UUID pedidoId, UUID compradorId, UUID vendedorId, long monto, String medio) {
        if (medio == null) {
            throw new DomainException("VALIDATION_ERROR", "Selecciona un medio de pago válido.");
        }
        MedioPago medioPago;
        try {
            medioPago = MedioPago.valueOf(medio);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("VALIDATION_ERROR", "Selecciona un medio de pago válido.");
        }
        Pago pago = pagos.guardar(Pago.pedidoPendiente(pedidoId, compradorId, vendedorId, monto, medioPago,
                Instant.now(clock)));
        return new PagoRegistrado(pago.id(), pago.estado().name(), pago.medio().name(), pago.monto());
    }
}
