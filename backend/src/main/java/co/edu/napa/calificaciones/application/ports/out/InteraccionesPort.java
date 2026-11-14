package co.edu.napa.calificaciones.application.ports.out;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lectura de pedidos, domicilios, emprendimientos, productos y usuarios que decide quién puede calificar a quién
 * (RF-01) y quién puede ver qué reputación (RF-02). No guarda copia de nada: siempre lee el estado vigente.
 */
public interface InteraccionesPort {
    Optional<ParticipantesPedido> participantes(UUID pedidoId);

    /** Pedido de un domicilio, para que el domiciliario llegue a sus opciones de calificación. */
    Optional<UUID> pedidoDeDomicilio(UUID domicilioId);

    /** Nombre visible de un emprendimiento, producto o usuario. */
    Optional<String> nombre(co.edu.napa.calificaciones.domain.model.TipoEntidad tipo, UUID entidadId);

    /** Dueño del emprendimiento, aunque esté inactivo o eliminado (histórico, RF-02 Edge 3). */
    Optional<UUID> duenioEmprendimiento(UUID emprendimientoId);

    /** Dueño del emprendimiento del producto. */
    Optional<UUID> duenioProducto(UUID productoId);

    Optional<String> rolUsuario(UUID usuarioId);

    boolean clienteComproAVendedor(UUID vendedorId, UUID clienteId);

    boolean domiciliarioLlevoPedidoDeVendedor(UUID vendedorId, UUID domiciliarioId);

    /**
     * {@code domiciliarioId} es el del domicilio ENTREGADO del pedido, o nulo si el pedido no tuvo domicilio
     * entregado.
     */
    record ParticipantesPedido(
            UUID pedidoId,
            UUID clienteId,
            UUID vendedorId,
            UUID emprendimientoId,
            String estado,
            Set<UUID> productoIds,
            UUID domiciliarioId
    ) {
        public ParticipantesPedido {
            productoIds = Set.copyOf(productoIds);
        }
    }
}
