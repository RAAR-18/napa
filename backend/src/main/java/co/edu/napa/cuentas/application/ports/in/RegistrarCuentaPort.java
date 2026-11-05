package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.RolUsuario;

import java.util.UUID;

/** RF-07: crea una cuenta pendiente de verificación y pide el código al celular. */
public interface RegistrarCuentaPort {
    RegistroResultado registrar(
            String nombre,
            String celular,
            String correo,
            String password,
            String confirmacionPassword,
            RolUsuario rol,
            String deviceId,
            String ipAddress
    );

    record RegistroResultado(UUID usuarioId, String estado, VerificacionInfo verificacion) {
    }

    /** Si el envío falló, estado es FALLIDO y la cuenta se conserva: el usuario puede reenviar. */
    record VerificacionInfo(UUID mensajeId, String canal, long reenvioDisponibleEn, String estado) {
    }
}