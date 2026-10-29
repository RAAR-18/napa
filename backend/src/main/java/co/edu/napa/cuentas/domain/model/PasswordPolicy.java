package co.edu.napa.cuentas.domain.model;

import co.edu.napa.shared.domain.DomainException;

/** Regla de contraseñas (decisión D14): entre 8 y 128 caracteres, sin reglas de composición. */
public final class PasswordPolicy {
    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 128;

    private PasswordPolicy() {
    }

    public static void validar(String password) {
        int longitud = password == null ? 0 : password.codePointCount(0, password.length());
        if (longitud < LONGITUD_MINIMA || longitud > LONGITUD_MAXIMA) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "La contraseña debe tener entre 8 y 128 caracteres."
            );
        }
    }
}