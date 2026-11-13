package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.pedidos.domain.model.ModalidadEntrega;

import java.time.Instant;
import java.util.UUID;

/**
 * Cierre de los pedidos que entrega el propio vendedor: entrega directa del ambulante (RF-36, RF-37) y recogida
 * en el punto fijo (RF-64, RF-65).
 */
public interface EntregaPedidoPort {
    PedidoTransicionado iniciarEntregaDirecta(UUID pedidoId, UUID vendedorId);

    PedidoEntregado confirmarEntregaDirecta(UUID pedidoId, UUID vendedorId, boolean cobroConfirmado);

    PedidoTransicionado marcarListoParaRecoger(UUID pedidoId, UUID vendedorId);

    PedidoEntregado confirmarRetiro(UUID pedidoId, UUID vendedorId, boolean cobroConfirmado);

    record PedidoTransicionado(UUID id, String estado, ModalidadEntrega modalidad, Instant reserva, Instant updatedAt) {}

    record PedidoEntregado(UUID id, String estado, ModalidadEntrega modalidad, Instant updatedAt, Pago pago) {}

    record Pago(UUID id, String estado, String medio, long monto, Instant confirmadoAt) {}
}
