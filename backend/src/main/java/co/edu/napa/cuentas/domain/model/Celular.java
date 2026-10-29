package co.edu.napa.cuentas.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.util.regex.Pattern;

/** Celular en formato E.164 (por ejemplo +573001234567). */
public record Celular(String valor) {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern MOVIL_COLOMBIANO = Pattern.compile("^3[0-9]{9}$");

    public Celular {
        if (valor == null || !E164.matcher(valor).matches()) {
            throw formatoInvalido();
        }
    }

    /** Normaliza lo que escribe el usuario: quita separadores, acepta 00 y móviles colombianos de 10 dígitos. */
    public static Celular desde(String entrada) {
        if (entrada == null) {
            throw formatoInvalido();
        }
        String celular = entrada.trim().replaceAll("[\\s().-]", "");
        if (celular.startsWith("00")) {
            celular = "+" + celular.substring(2);
        } else if (MOVIL_COLOMBIANO.matcher(celular).matches()) {
            celular = "+57" + celular;
        }
        return new Celular(celular);
    }

    private static DomainException formatoInvalido() {
        return new DomainException(
                "VALIDATION_ERROR",
                "El celular debe estar en formato internacional, por ejemplo +573001234567."
        );
    }
}