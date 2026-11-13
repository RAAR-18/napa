package co.edu.napa.pagos.application.ports.in;

import java.util.UUID;

public interface RegistrarPagoPedidoPendientePort {
    PagoRegistrado registrar(UUID pedidoId, UUID compradorId, UUID vendedorId, long monto, String medio);

    record PagoRegistrado(UUID id, String estado, String medio, long monto) {}
}
