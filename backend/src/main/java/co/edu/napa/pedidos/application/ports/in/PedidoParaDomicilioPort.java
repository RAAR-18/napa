package co.edu.napa.pedidos.application.ports.in;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Lo que el módulo {@code domicilios} necesita del pedido (RF-14, RF-13). */
public interface PedidoParaDomicilioPort {
    /** Bloquea el pedido hasta el fin de la transacción y devuelve sus datos, o vacío si no existe. */
    Optional<PedidoParaDomicilio> bloquear(UUID pedidoId);

    /** Cantidad de ítems (productos distintos) del pedido, para el resumen que ve el domiciliario (RF-18). */
    int cantidadItems(UUID pedidoId);

    /** Resumen del pedido para el administrador (RF-12); vacío si no existe o no es de ese comprador. */
    Optional<ResumenPedido> resumen(UUID pedidoId, UUID compradorId);

    /** Pasa el pedido a {@code CANCELADO} porque su domicilio se canceló. */
    void cancelarPorDomicilio(UUID pedidoId);

    /** Pasa el pedido a {@code EN_CAMINO} porque el vendedor confirmó la salida del domiciliario (RF-23). */
    void ponerEnCaminoPorDomicilio(UUID pedidoId);

    /** Pasa el pedido a {@code ENTREGADO} y publica E07 con el domicilio que lo entregó (RF-24). */
    void entregarPorDomicilio(UUID pedidoId, UUID domicilioId, UUID domiciliarioId);

    record PedidoParaDomicilio(
            UUID id,
            UUID compradorId,
            UUID emprendimientoId,
            String modalidad,
            String estado,
            Long tarifaDomicilio,
            UUID ubicacionEntregaId,
            Instant reserva
    ) {}

    record ResumenPedido(UUID id, String modalidad, String estado, long total, int cantidadItems) {}
}
