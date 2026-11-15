package co.edu.napa.pedidos.application.ports.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CanalContactoRepositoryPort {
    /** Dueño del emprendimiento del pedido, aunque el emprendimiento ya no esté activo. */
    Optional<UUID> vendedorDelPedido(UUID pedidoId);

    void registrar(UUID id, UUID pedidoId, UUID clienteId, UUID destinatarioId, String destinatario, String tipo,
                   Instant ahora);
}
