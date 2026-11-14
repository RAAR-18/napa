package co.edu.napa.domicilios.application.ports.in;

import java.time.Instant;
import java.util.UUID;

/** RF-14: el vendedor de punto fijo publica el domicilio de un pedido aceptado y pagado. */
public interface PublicarDomicilioPort {
    DomicilioPublicado publicar(UUID pedidoId, UUID vendedorId);

    record DomicilioPublicado(
            UUID id,
            UUID pedidoId,
            String estado,
            long ganancia,
            Instant createdAt,
            Punto origen,
            Punto destino
    ) {}

    record Punto(double latitud, double longitud, String direccion, String referencia) {}
}
