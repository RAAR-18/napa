package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;

import java.util.UUID;

/** RF-07: crea una cuenta pendiente de verificación y envía el código por SMS o por correo. */
public interface RegistrarCuentaPort {
    default RegistroResultado registrar(
            String nombre,
            String celular,
            String correo,
            String password,
            String confirmacionPassword,
            RolUsuario rol,
            String deviceId,
            String ipAddress
    ) {
        return registrar(nombre, celular, correo, password, confirmacionPassword, rol, deviceId, ipAddress,
                CanalVerificacion.WHATSAPP);
    }

    RegistroResultado registrar(
            String nombre,
            String celular,
            String correo,
            String password,
            String confirmacionPassword,
            RolUsuario rol,
            String deviceId,
            String ipAddress,
            CanalVerificacion canal
    );

    record RegistroResultado(UUID usuarioId, String estado, VerificacionInfo verificacion) {
    }

    /** Si el envío falló, estado es FALLIDO y la cuenta se conserva: el usuario puede reenviar. */
    record VerificacionInfo(UUID mensajeId, String canal, long reenvioDisponibleEn, String estado) {
    }
}