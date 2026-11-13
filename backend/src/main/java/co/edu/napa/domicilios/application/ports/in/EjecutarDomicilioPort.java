package co.edu.napa.domicilios.application.ports.in;

import java.time.Instant;
import java.util.UUID;

/** Avance del domicilio después de asignarse: recogida, salida (RF-23) y entrega con código (RF-24). */
public interface EjecutarDomicilioPort {
    /** El domiciliario asignado confirma que recogió el pedido: {@code ASIGNADO → RECOGIDO}. */
    Transicion confirmarRecogida(UUID domiciliarioId, UUID domicilioId);

    /** El vendedor dueño confirma la salida: {@code RECOGIDO → EN_CAMINO}; el pedido pasa a {@code EN_CAMINO}. */
    Transicion confirmarSalida(UUID vendedorId, UUID domicilioId);

    /** El domiciliario asignado entrega con el código del cliente: domicilio y pedido pasan a {@code ENTREGADO}. */
    Transicion confirmarEntrega(UUID domiciliarioId, UUID domicilioId, String codigo);

    record Transicion(UUID id, String estado, Instant updatedAt) {}
}
