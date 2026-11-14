package co.edu.napa.reportes.domain.model;

/** RF-60 Data Rules: el motivo del reporte. Los cuatro primeros exigen la entidad relacionada. */
public enum MotivoReporte {
    PEDIDO(true),
    DOMICILIO(true),
    PAGO(true),
    COMPORTAMIENTO_USUARIO(true),
    PLATAFORMA(false),
    OTRO(false);

    private final boolean requiereEntidad;

    MotivoReporte(boolean requiereEntidad) {
        this.requiereEntidad = requiereEntidad;
    }

    public boolean requiereEntidad() {
        return requiereEntidad;
    }
}
