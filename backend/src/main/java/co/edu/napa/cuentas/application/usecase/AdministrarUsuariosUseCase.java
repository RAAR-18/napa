package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AdministrarUsuariosPort;
import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;
import co.edu.napa.cuentas.application.ports.out.GestionUsuariosRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.GestionUsuariosRepositoryPort.RolEstado;
import co.edu.napa.cuentas.application.ports.out.OperacionesPendientesPort;
import co.edu.napa.cuentas.application.ports.out.OperacionesPendientesPort.OperacionesPendientes;
import co.edu.napa.cuentas.domain.event.CuentaEliminadaEvent;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** RF-66 a RF-69: el administrador consulta, edita, elimina y lista usuarios. */
@Component
public class AdministrarUsuariosUseCase implements AdministrarUsuariosPort {
    private static final Set<String> ROLES_ASIGNABLES =
            Set.of("CLIENTE", "VENDEDOR_AMBULANTE", "VENDEDOR_PUNTO_FIJO", "DOMICILIARIO");
    private static final String ADMINISTRADOR = "ADMINISTRADOR";

    private final GestionUsuariosRepositoryPort usuarios;
    private final OperacionesPendientesPort pendientes;
    private final CierreCuenta cierre;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public AdministrarUsuariosUseCase(
            GestionUsuariosRepositoryPort usuarios,
            OperacionesPendientesPort pendientes,
            CierreCuenta cierre,
            EventPublisherPort eventos,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.pendientes = Objects.requireNonNull(pendientes, "pendientes");
        this.cierre = Objects.requireNonNull(cierre, "cierre");
        this.eventos = Objects.requireNonNull(eventos, "eventos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina listar(Filtro filtro, int page, int size, String sortCampo, boolean ascendente) {
        Objects.requireNonNull(filtro, "filtro");
        return new Pagina(usuarios.listar(filtro, page, size, sortCampo, ascendente), page, size,
                usuarios.contar(filtro));
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilUsuario consultar(UUID usuarioId) {
        return usuarios.perfil(Objects.requireNonNull(usuarioId, "usuarioId")).orElseThrow(AdministrarUsuariosUseCase::noExiste);
    }

    @Override
    @Transactional
    public PerfilUsuario editar(UUID administradorId, UUID usuarioId, Cambios cambios) {
        Objects.requireNonNull(administradorId, "administradorId");
        Objects.requireNonNull(cambios, "cambios");
        if (cambios.vacio()) {
            throw ReglasPerfil.invalido("nombre", "Envía al menos un campo para actualizar.");
        }
        String nombre = cambios.nombre() == null ? null : ReglasPerfil.nombre(cambios.nombre());
        String celular = cambios.celular() == null ? null : ReglasPerfil.celular(cambios.celular());
        String correo = cambios.correo() == null ? null : ReglasPerfil.correo(cambios.correo());
        PerfilUsuario.Ubicacion ubicacion = cambios.ubicacion() == null ? null : ReglasPerfil.ubicacion(cambios.ubicacion());
        String rol = cambios.rol();
        if (rol != null && !ROLES_ASIGNABLES.contains(rol)) {
            if (ADMINISTRADOR.equals(rol)) {
                throw new DomainException("BUSINESS_RULE", "Las cuentas de administrador no se editan aquí.");
            }
            throw ReglasPerfil.invalido("rol", "Rol desconocido.");
        }

        RolEstado actual = usuarios.bloquear(Objects.requireNonNull(usuarioId, "usuarioId"))
                .orElseThrow(AdministrarUsuariosUseCase::noExiste);
        if (ADMINISTRADOR.equals(actual.rol())) {
            throw new DomainException("BUSINESS_RULE", "Las cuentas de administrador no se editan aquí.");
        }
        if (!"ACTIVA".equals(actual.estado())) {
            throw new DomainException("INVALID_STATE", "La cuenta no está activa.",
                    Map.of("estadoActual", actual.estado()));
        }
        boolean cambiaRol = rol != null && !rol.equals(actual.rol());
        if (cambiaRol) {
            OperacionesPendientes operaciones = pendientes.contar(usuarioId);
            if (operaciones.hayAlguna()) {
                throw new DomainException("HAS_PENDING_OPERATIONS",
                        "El usuario tiene operaciones pendientes; no se puede cambiar su rol.", operaciones.detalles());
            }
        }

        Instant ahora = clock.instant();
        if (nombre != null) {
            usuarios.actualizarNombre(usuarioId, nombre, ahora);
        }
        if (celular != null) {
            usuarios.actualizarCelular(usuarioId, celular, ahora);
        }
        if (correo != null) {
            usuarios.actualizarCorreo(usuarioId, correo, ahora);
        }
        if (ubicacion != null) {
            usuarios.guardarUbicacion(usuarioId, ubicacion, ahora);
        }
        if (cambiaRol) {
            usuarios.actualizarRol(usuarioId, rol, ahora);
            // El JWT lleva el rol: el próximo token debe traer el nuevo.
            cierre.cerrarSesiones(usuarioId, ahora);
        }
        return consultar(usuarioId);
    }

    @Override
    @Transactional
    public void eliminar(UUID administradorId, UUID usuarioId, boolean forzar) {
        Objects.requireNonNull(administradorId, "administradorId");
        RolEstado actual = usuarios.bloquear(Objects.requireNonNull(usuarioId, "usuarioId"))
                .orElseThrow(AdministrarUsuariosUseCase::noExiste);
        if (ADMINISTRADOR.equals(actual.rol()) || administradorId.equals(usuarioId)) {
            throw new DomainException("BUSINESS_RULE", "Esta cuenta no se puede eliminar aquí.");
        }
        if ("ELIMINADA".equals(actual.estado())) {
            throw new DomainException("INVALID_STATE", "La cuenta ya fue eliminada.",
                    Map.of("estadoActual", actual.estado()));
        }
        OperacionesPendientes operaciones = pendientes.contar(usuarioId);
        if (operaciones.hayAlguna() && !forzar) {
            throw new DomainException("HAS_PENDING_OPERATIONS",
                    "El usuario tiene actividad en curso; confirma con forzar para eliminarlo.", operaciones.detalles());
        }
        Instant ahora = clock.instant();
        usuarios.eliminarEmprendimientoActivo(usuarioId, ahora);
        usuarios.marcarEliminada(usuarioId, ahora);
        cierre.cerrarSesiones(usuarioId, ahora);
        eventos.publish(CuentaEliminadaEvent.de(usuarioId, administradorId, ahora));
    }

    private static DomainException noExiste() {
        return new DomainException("NOT_FOUND", "El usuario no existe.");
    }
}
