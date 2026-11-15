package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;

import java.util.UUID;

/** RF-08: cambio de celular en dos pasos (código al número nuevo) y foto de perfil. */
public interface CambiarCelularYFotoPort {
    SolicitarVerificacionPort.EnvioSolicitado solicitarCambioCelular(
            UUID usuarioId, String rol, String celular, CanalVerificacion canal, String ipAddress);

    PerfilUsuario confirmarCambioCelular(UUID usuarioId, String rol, String codigo);

    /** Valida el tipo real (por contenido) y el tamaño; devuelve la URL nueva. */
    String cambiarFoto(UUID usuarioId, byte[] contenido);
}
