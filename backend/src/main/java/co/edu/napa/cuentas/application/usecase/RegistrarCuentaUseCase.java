package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.RegistrarCuentaPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.PasswordPolicy;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class RegistrarCuentaUseCase implements RegistrarCuentaPort {
    private static final int LONGITUD_MAXIMA_NOMBRE = 120;

    private final UsuarioRepositoryPort usuarios;
    private final VerificarCelularPort verificarCelular;
    private final PasswordHashingPort passwordHashing;
    private final Clock clock;

    public RegistrarCuentaUseCase(
            UsuarioRepositoryPort usuarios,
            VerificarCelularPort verificarCelular,
            PasswordHashingPort passwordHashing,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
        this.passwordHashing = Objects.requireNonNull(passwordHashing, "passwordHashing");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public RegistroResultado registrar(
            String nombre,
            String celularInput,
            String correoInput,
            String password,
            String confirmacionPassword,
            RolUsuario rol,
            String deviceId,
            String ipAddress
    ) {
        String celular = Celular.desde(celularInput).valor();
        String correo = normalizarCorreo(correoInput);
        validarNombre(nombre);
        PasswordPolicy.validar(password);
        if (!Objects.equals(password, confirmacionPassword)) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "La contraseña y su confirmación deben coincidir.",
                    Map.of("field", "confirmacionPassword")
            );
        }
        if (rol == null || rol == RolUsuario.ADMINISTRADOR) {
            throw new DomainException("VALIDATION_ERROR", "Selecciona un rol válido para registrarte.");
        }
        Sesion.validarDeviceId(deviceId);
        if (usuarios.existsByCelular(celular)) {
            throw new DomainException("PHONE_ALREADY_REGISTERED", "Ese celular ya está registrado.");
        }
        if (correo != null && usuarios.existsByCorreo(correo)) {
            throw new DomainException("EMAIL_ALREADY_REGISTERED", "Ese correo ya está registrado.");
        }

        Instant now = Instant.now(clock);
        Usuario usuario = new Usuario(
                UUID.randomUUID(),
                nombre.trim(),
                celular,
                correo,
                passwordHashing.hash(password),
                rol,
                EstadoUsuario.PENDIENTE_VERIFICACION,
                null,
                now,
                now
        );
        usuarios.save(usuario);
        verificarCelular.solicitar(celular, EstadoVerificacion.REGISTRO, ipAddress);
        return new RegistroResultado(usuario.id(), usuario.estado().name());
    }

    private String normalizarCorreo(String correoInput) {
        if (correoInput == null || correoInput.isBlank()) {
            return null;
        }
        return correoInput.trim().toLowerCase(Locale.ROOT);
    }

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "El nombre es obligatorio y no puede superar 120 caracteres."
            );
        }
    }
}