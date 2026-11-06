package co.edu.napa.notificaciones.domain.model;

import java.util.Arrays;

public enum TipoNotificacion {
    PEDIDO_CREADO("pedidos.pedido-creado"),
    PEDIDO_ACEPTADO("pedidos.pedido-aceptado"),
    PEDIDO_RECHAZADO("pedidos.pedido-rechazado"),
    PAGO_PEDIDO_CONFIRMADO("pagos.pago-pedido-confirmado"),
    ENTREGA_DIRECTA_INICIADA("entregas.entrega-directa-iniciada"),
    PEDIDO_LISTO_PARA_RECOGER("pedidos.pedido-listo-para-recoger"),
    DOMICILIO_PUBLICADO("domicilios.domicilio-publicado"),
    DOMICILIO_ASIGNADO("domicilios.domicilio-asignado"),
    DOMICILIO_RECOGIDO("domicilios.domicilio-recogido"),
    DOMICILIO_EN_CAMINO("domicilios.domicilio-en-camino"),
    DOMICILIO_ENTREGADO("domicilios.domicilio-entregado"),
    PAGO_TARIFA_REGISTRADO("pagos.pago-tarifa-registrado"),
    DOMICILIO_CANCELADO("domicilios.domicilio-cancelado"),
    REPORTE_RESUELTO("reportes.reporte-resuelto"),
    COMENTARIO_MODERADO("calificaciones.comentario-moderado");

    private final String eventType;

    TipoNotificacion(String eventType) {
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }

    public static TipoNotificacion fromEventType(String eventType) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.eventType.equals(eventType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported notification event type"));
    }
}
