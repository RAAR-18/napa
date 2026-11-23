package co.edu.napa.cuentas.domain.model;

import co.edu.napa.shared.domain.DomainException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Correo de una cuenta: formato estricto (rechaza "ana@gmail,com" o "ana@gmail") y detección de
 * errores de escritura en los proveedores comunes ("gmial.com" sugiere "gmail.com").
 * Que el dominio exista y reciba correo lo comprueba DominioCorreoPort.
 */
public record Correo(String valor) {
    private static final int LONGITUD_MAXIMA = 254;
    private static final Pattern FORMATO = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9._%+-]{0,62}[a-z0-9_%+-])?"
                    + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}$"
    );
    /** Proveedores frecuentes en Colombia; se usan solo para sugerir correcciones. */
    public static final List<String> DOMINIOS_COMUNES = List.of(
            "gmail.com", "hotmail.com", "hotmail.es", "outlook.com", "outlook.es", "live.com",
            "yahoo.com", "yahoo.es", "icloud.com", "msn.com", "protonmail.com", "proton.me", "aol.com"
    );

    public Correo {
        if (valor == null) {
            throw invalido("Escribe un correo.");
        }
    }

    public static Correo desde(String entrada) {
        String texto = entrada == null ? "" : entrada.strip().toLowerCase(Locale.ROOT);
        if (texto.isEmpty()) {
            throw invalido("Escribe un correo.");
        }
        if (texto.length() > LONGITUD_MAXIMA || !FORMATO.matcher(texto).matches() || texto.contains("..")) {
            throw invalido("El correo no tiene un formato válido. Revísalo, por ejemplo: nombre@gmail.com.");
        }
        String dominio = texto.substring(texto.indexOf('@') + 1);
        String sugerido = sugerencia(dominio);
        if (sugerido != null) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "El dominio \"" + dominio + "\" no coincide con ningún correo conocido. ¿Quisiste decir "
                            + texto.substring(0, texto.indexOf('@') + 1) + sugerido + "?",
                    Map.of("field", "correo", "sugerencia", sugerido)
            );
        }
        return new Correo(texto);
    }

    public String dominio() {
        return valor.substring(valor.indexOf('@') + 1);
    }

    /** Dominio común muy parecido (a uno o dos cambios) pero distinto; null si no hay. */
    static String sugerencia(String dominio) {
        if (DOMINIOS_COMUNES.contains(dominio)) {
            return null;
        }
        String mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (String comun : DOMINIOS_COMUNES) {
            int distancia = distancia(dominio, comun);
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia;
                mejor = comun;
            }
        }
        return mejorDistancia <= 2 ? mejor : null;
    }

    private static int distancia(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previa[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + costo);
            }
            int[] temporal = previa;
            previa = actual;
            actual = temporal;
        }
        return previa[b.length()];
    }

    private static DomainException invalido(String mensaje) {
        return new DomainException("VALIDATION_ERROR", mensaje, Map.of("field", "correo"));
    }
}
