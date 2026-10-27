package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.RecuperarContrasenaPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.Contrasena;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;
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

@Component
public class RecuperarContrasenaUseCase implements RecuperarContrasenaPort {
    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final SessionVersionPort sessionVersions;
    private final VerificarCelularPort verificarCelular;
    private final PasswordHashingPort passwordHashing;
    private final Clock clock;

    public RecuperarContrasenaUseCase(
            UsuarioRepositoryPort usuarios,
            SesionRepositoryPort sesiones,
            SessionVersionPort sessionVersions,
            VerificarCelularPort verificarCelular,
            PasswordHashingPort passwordHashing,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.sessionVersions = Objects.requireNonNull(sessionVersions, "sessionVersions");
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
        this.passwordHashing = Objects.requireNonNull(passwordHashing, "passwordHashing");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void recuperar(String celularInput, String codigo, String contrasenaNueva) {
        String celular = Celular.desde(celularInput).valor();
        Contrasena.validar(contrasenaNueva);
        VerificacionResultado resultado = verificarCelular.confirmar(
                celular,
                EstadoVerificacion.RECUPERACION_CONTRASENA,
                codigo
        );
        if (resultado.estado() != VerificacionResultado.Estado.APROBADA) {
            throw new DomainException("VALIDATION_ERROR", "El código no es válido o venció.");
        }
        Usuario usuario = usuarios.findByCelular(celular).orElse(null);
        if (usuario == null || usuario.estado() != EstadoUsuario.ACTIVA
                || usuario.rol() == RolUsuario.ADMINISTRADOR) {
            // Misma respuesta que si todo saliera bien: no revelar qué números tienen cuenta.
            return;
        }
        Instant now = Instant.now(clock);
        usuarios.save(usuario.cambiarContrasena(passwordHashing.hash(contrasenaNueva), now));
        revocar(sesiones.revokeAllSessions(usuario.id(), now), now);
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