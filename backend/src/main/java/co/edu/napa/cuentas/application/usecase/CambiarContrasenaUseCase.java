package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.CambiarContrasenaPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.Contrasena;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class CambiarContrasenaUseCase implements CambiarContrasenaPort {
    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final SessionVersionPort sessionVersions;
    private final PasswordHashingPort passwordHashing;
    private final Clock clock;

    public CambiarContrasenaUseCase(
            UsuarioRepositoryPort usuarios,
            SesionRepositoryPort sesiones,
            SessionVersionPort sessionVersions,
            PasswordHashingPort passwordHashing,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.sessionVersions = Objects.requireNonNull(sessionVersions, "sessionVersions");
        this.passwordHashing = Objects.requireNonNull(passwordHashing, "passwordHashing");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void cambiar(UUID usuarioId, UUID sesionActualId, String contrasenaActual, String contrasenaNueva) {
        Contrasena.validar(contrasenaNueva);
        Usuario usuario = usuarios.findById(usuarioId)
                .filter(cuenta -> cuenta.estado() == EstadoUsuario.ACTIVA)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "La cuenta no está activa."));
        if (!passwordHashing.matches(contrasenaActual, usuario.passwordHash())) {
            throw new DomainException("INVALID_CURRENT_PASSWORD", "La contraseña actual es incorrecta.");
        }
        // La actual ya se verificó, así que basta comparar los textos para saber si es la misma.
        if (contrasenaNueva.equals(contrasenaActual)) {
            throw new DomainException("PASSWORD_REUSED", "Elige una contraseña distinta.");
        }
        Instant now = Instant.now(clock);
        usuarios.save(usuario.cambiarContrasena(passwordHashing.hash(contrasenaNueva), now));
        revocar(sesiones.revokeOtherSessions(usuarioId, sesionActualId, now), now);
    }

    private void revocar(List<Sesion> revocadas, Instant now) {
        for (Sesion sesion : revocadas) {
            sessionVersions.revoke(
                    sesion.id(),
                    sesion.sessionVersion(),
                    Duration.between(now, sesion.expiresAt())
            );
        }
    }
}