package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.GestionarCuentaPropiaPort;
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
import java.util.UUID;

/** RF-08 (perfil: nombre y ubicación) y RF-11 (eliminar la propia cuenta sin operaciones pendientes). */
@Component
public class GestionarCuentaPropiaUseCase implements GestionarCuentaPropiaPort {
    private final GestionUsuariosRepositoryPort usuarios;
    private final OperacionesPendientesPort pendientes;
    private final CierreCuenta cierre;
    private final EventPublisherPort eventos;
    private final Clock clock;

    public GestionarCuentaPropiaUseCase(
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
    public PerfilUsuario perfil(UUID usuarioId) {
        return usuarios.perfil(Objects.requireNonNull(usuarioId, "usuarioId"))
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "Debes iniciar sesión para continuar."));
    }

    @Override
    @Transactional
    public PerfilUsuario editar(UUID usuarioId, String nombre, PerfilUsuario.Ubicacion ubicacion) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        if (nombre == null && ubicacion == null) {
            throw ReglasPerfil.invalido("nombre", "Envía al menos un campo para actualizar.");
        }
        String nombreValido = nombre == null ? null : ReglasPerfil.nombre(nombre);
        PerfilUsuario.Ubicacion ubicacionValida = ubicacion == null ? null : ReglasPerfil.ubicacion(ubicacion);
        usuarios.bloquear(usuarioId)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "Debes iniciar sesión para continuar."));
        Instant ahora = clock.instant();
        if (nombreValido != null) {
            usuarios.actualizarNombre(usuarioId, nombreValido, ahora);
        }
        if (ubicacionValida != null) {
            usuarios.guardarUbicacion(usuarioId, ubicacionValida, ahora);
        }
        return perfil(usuarioId);
    }

    @Override
    @Transactional
    public void eliminar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        RolEstado actual = usuarios.bloquear(usuarioId)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "Debes iniciar sesión para continuar."));
        if ("ADMINISTRADOR".equals(actual.rol())) {
            throw new DomainException("FORBIDDEN", "Esta cuenta no se puede eliminar aquí.");
        }
        if (!"ACTIVA".equals(actual.estado())) {
            throw new DomainException("INVALID_STATE", "La cuenta no está activa.",
                    Map.of("estadoActual", actual.estado()));
        }
        OperacionesPendientes operaciones = pendientes.contar(usuarioId);
        if (operaciones.hayAlguna()) {
            throw new DomainException("HAS_PENDING_OPERATIONS",
                    "Resuelve tus operaciones pendientes antes de eliminar la cuenta.", operaciones.detalles());
        }
        Instant ahora = clock.instant();
        usuarios.marcarEliminada(usuarioId, ahora);
        cierre.cerrarSesiones(usuarioId, ahora);
        eventos.publish(CuentaEliminadaEvent.de(usuarioId, usuarioId, ahora));
    }
}
