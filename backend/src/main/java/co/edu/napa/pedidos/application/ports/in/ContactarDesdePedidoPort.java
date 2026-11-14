package co.edu.napa.pedidos.application.ports.in;

import java.util.UUID;

/** RF-49: el cliente abre una llamada o un chat con el vendedor o el domiciliario de su pedido. */
public interface ContactarDesdePedidoPort {
    CanalContacto contactar(UUID clienteId, UUID pedidoId, Destinatario destinatario, Tipo tipo);

    enum Destinatario { VENDEDOR, DOMICILIARIO }

    enum Tipo { MENSAJE, LLAMADA }

    /** {@code enlace} es {@code tel:+57…} para llamada o {@code https://wa.me/57…} para mensaje. */
    record CanalContacto(UUID id, Destinatario destinatario, Tipo tipo, String enlace) {}
}
