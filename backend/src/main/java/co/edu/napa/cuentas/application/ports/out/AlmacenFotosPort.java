package co.edu.napa.cuentas.application.ports.out;

import java.util.UUID;

/** RF-08: guarda la foto de perfil y devuelve su URL pública. */
public interface AlmacenFotosPort {
    /** Lanza {@code EXTERNAL_SERVICE_UNAVAILABLE} si el almacenamiento no responde. */
    String guardar(UUID usuarioId, byte[] contenido, String extension);
}
