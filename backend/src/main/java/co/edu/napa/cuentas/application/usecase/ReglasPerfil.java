package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.shared.domain.DomainException;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Validaciones de los datos de perfil que comparten RF-08 y RF-67. */
final class ReglasPerfil {
    static final int NOMBRE_MAXIMO = 120;
    private static final int TEXTO_UBICACION_MAXIMO = 300;
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ReglasPerfil() {
    }

    static String nombre(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty() || texto.length() > NOMBRE_MAXIMO) {
            throw invalido("nombre", "El nombre es obligatorio y no puede superar 120 caracteres.");
        }
        return texto;
    }

    static String celular(String celular) {
        try {
            return Celular.desde(celular).valor();
        } catch (DomainException e) {
            throw invalido("celular", e.getMessage());
        }
    }

    static String correo(String correo) {
        String texto = correo == null ? "" : correo.strip().toLowerCase(Locale.ROOT);
        if (texto.length() > 254 || !CORREO.matcher(texto).matches()) {
            throw invalido("correo", "El correo no tiene un formato válido.");
        }
        return texto;
    }

    static PerfilUsuario.Ubicacion ubicacion(PerfilUsuario.Ubicacion ubicacion) {
        if (ubicacion.latitud() < -90 || ubicacion.latitud() > 90) {
            throw invalido("ubicacion.latitud", "La latitud debe estar entre -90 y 90.");
        }
        if (ubicacion.longitud() < -180 || ubicacion.longitud() > 180) {
            throw invalido("ubicacion.longitud", "La longitud debe estar entre -180 y 180.");
        }
        String direccion = ubicacion.direccion() == null ? "" : ubicacion.direccion().strip();
        if (direccion.isEmpty() || direccion.length() > TEXTO_UBICACION_MAXIMO) {
            throw invalido("ubicacion.direccion", "La dirección es obligatoria y no puede superar 300 caracteres.");
        }
        String referencia = ubicacion.referencia() == null || ubicacion.referencia().isBlank()
                ? null : ubicacion.referencia().strip();
        if (referencia != null && referencia.length() > TEXTO_UBICACION_MAXIMO) {
            throw invalido("ubicacion.referencia", "La referencia no puede superar 300 caracteres.");
        }
        return new PerfilUsuario.Ubicacion(ubicacion.latitud(), ubicacion.longitud(), direccion, referencia);
    }

    static DomainException invalido(String campo, String mensaje) {
        return new DomainException("VALIDATION_ERROR", mensaje, Map.of("fields", Map.of(campo, mensaje)));
    }
}
