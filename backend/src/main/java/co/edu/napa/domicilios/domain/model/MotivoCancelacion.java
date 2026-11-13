package co.edu.napa.domicilios.domain.model;

public enum MotivoCancelacion {
    /** Nadie tomó el domicilio en el plazo máximo (RF-14 FR-009). */
    SIN_ASIGNAR,
    /** Lo canceló un administrador (RF-13). */
    ADMINISTRADOR
}
