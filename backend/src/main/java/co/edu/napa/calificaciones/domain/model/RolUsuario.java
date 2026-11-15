package co.edu.napa.calificaciones.domain.model;

import co.edu.napa.shared.domain.DomainException;

/** Rol de quien califica o consulta, tomado de la sesión. */
public enum RolUsuario {
    CLIENTE,
    VENDEDOR_AMBULANTE,
    VENDEDOR_PUNTO_FIJO,
    DOMICILIARIO,
    ADMINISTRADOR;

    public static RolUsuario de(String valor) {
        if (valor != null) {
            for (RolUsuario rol : values()) {
                if (rol.name().equals(valor)) {
                    return rol;
                }
            }
        }
        throw new DomainException("FORBIDDEN", "No tienes permiso para realizar esta acción.");
    }

    public boolean esVendedor() {
        return this == VENDEDOR_AMBULANTE || this == VENDEDOR_PUNTO_FIJO;
    }
}
