package co.edu.napa.cuentas.application.ports.in;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Datos que cualquier participante de una entrega puede ver: nombre y foto, nunca celular ni correo (RF-27). */
public interface ConsultarPerfilPublicoUsuarioPort {
    Optional<PerfilPublico> consultarPerfil(UUID usuarioId);

    /** {@code rol} y {@code miembroDesde} los usa la tarjeta del vendedor (RF-34). */
    record PerfilPublico(String nombre, String fotoUrl, String rol, Instant miembroDesde) {
        public PerfilPublico(String nombre, String fotoUrl) {
            this(nombre, fotoUrl, null, null);
        }
    }
}
