package co.edu.napa.cuentas.application.ports.in;

import java.util.UUID;

/** RF-08 (consultar y editar el propio perfil) y RF-11 (eliminar la propia cuenta). */
public interface GestionarCuentaPropiaPort {
    PerfilUsuario perfil(UUID usuarioId);

    /** {@code nombre} o {@code ubicacion} nulos no cambian. */
    PerfilUsuario editar(UUID usuarioId, String nombre, PerfilUsuario.Ubicacion ubicacion);

    void eliminar(UUID usuarioId);
}
