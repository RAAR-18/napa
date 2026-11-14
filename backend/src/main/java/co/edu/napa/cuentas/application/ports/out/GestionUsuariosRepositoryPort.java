package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.application.ports.in.AdministrarUsuariosPort;
import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lecturas y escrituras de perfil que no pasan por el agregado de autenticación. */
public interface GestionUsuariosRepositoryPort {
    Optional<PerfilUsuario> perfil(UUID usuarioId);

    /** Bloquea la fila del usuario hasta el fin de la transacción. */
    Optional<RolEstado> bloquear(UUID usuarioId);

    void actualizarNombre(UUID usuarioId, String nombre, Instant ahora);

    /** Actualiza la ubicación propia del usuario o la crea si no tiene. */
    void guardarUbicacion(UUID usuarioId, PerfilUsuario.Ubicacion ubicacion, Instant ahora);

    /** Lanza {@code PHONE_ALREADY_REGISTERED} o {@code EMAIL_ALREADY_REGISTERED} si el dato es de otra cuenta. */
    void actualizarCelular(UUID usuarioId, String celular, Instant ahora);

    void actualizarCorreo(UUID usuarioId, String correo, Instant ahora);

    void actualizarRol(UUID usuarioId, String rol, Instant ahora);

    void marcarEliminada(UUID usuarioId, Instant ahora);

    /** RF-68: el emprendimiento ACTIVO del vendedor pasa a ELIMINADO. */
    void eliminarEmprendimientoActivo(UUID usuarioId, Instant ahora);

    List<AdministrarUsuariosPort.UsuarioResumen> listar(AdministrarUsuariosPort.Filtro filtro, int page, int size,
                                                       String sortCampo, boolean ascendente);

    long contar(AdministrarUsuariosPort.Filtro filtro);

    record RolEstado(String rol, String estado) {}
}
