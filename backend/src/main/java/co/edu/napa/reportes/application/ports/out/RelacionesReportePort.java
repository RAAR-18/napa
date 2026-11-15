package co.edu.napa.reportes.application.ports.out;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lectura de pedidos, domicilios y usuarios para validar lo que se reporta (RF-60) y resumirlo (RF-61). */
public interface RelacionesReportePort {
    /** El autor es cliente o vendedor del pedido, o domiciliario de un domicilio del pedido. */
    boolean participaEnPedido(UUID autorId, UUID pedidoId);

    /** El autor es el comprador, el vendedor o el domiciliario del domicilio. */
    boolean participaEnDomicilio(UUID autorId, UUID domicilioId);

    /** Ambos usuarios compartieron un pedido (cliente, vendedor o domiciliario). */
    boolean compartieronPedido(UUID autorId, UUID usuarioId);

    Optional<Usuario> usuario(UUID usuarioId);

    /** Resumen para el detalle del administrador; vacío si la entidad ya no existe. */
    Optional<Map<String, Object>> resumenPedido(UUID pedidoId);

    Optional<Map<String, Object>> resumenDomicilio(UUID domicilioId);

    record Usuario(UUID id, String nombre, String rol, String estado) {}
}
