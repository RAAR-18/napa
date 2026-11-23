package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.RegistrarCuentaPort;
import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.DominioCorreoPort;
import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.Correo;
import co.edu.napa.cuentas.domain.model.PasswordPolicy;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class RegistrarCuentaUseCase implements RegistrarCuentaPort {
    private static final int LONGITUD_MAXIMA_NOMBRE = 120;

    private final UsuarioRepositoryPort usuarios;
    private final SolicitarVerificacionPort solicitarVerificacion;
    private final PasswordHashingPort passwordHashing;
    private final DominioCorreoPort dominioCorreo;
    private final Clock clock;

    public RegistrarCuentaUseCase(
            UsuarioRepositoryPort usuarios,
            SolicitarVerificacionPort solicitarVerificacion,
            PasswordHashingPort passwordHashing,
            DominioCorreoPort dominioCorreo,
            Clock clock
    ) {
        this.dominioCorreo = Objects.requireNonNull(dominioCorreo, "dominioCorreo");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.solicitarVerificacion = Objects.requireNonNull(solicitarVerificacion, "solicitarVerificacion");
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
            String ipAddress,
            CanalVerificacion canalInput
    ) {
        String celular = Celular.desde(celularInput).valor();
        String correo = normalizarCorreo(correoInput);
        // Sin canal la cuenta se crea sin enviar nada: la app pregunta antes si prefiere SMS o correo.
        CanalVerificacion canal = canalInput;
        if (canal == CanalVerificacion.CORREO && correo == null) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "Escribe tu correo para recibir el código por correo, o elige SMS.",
                    Map.of("field", "correo")
            );
        }
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
        // Una cuenta que nunca se verificó no reserva nada: se descarta y se registra la nueva.
        usuarios.findByCelular(celular)
                .filter(existente -> existente.estado() == EstadoUsuario.PENDIENTE_VERIFICACION)
                .ifPresent(existente -> usuarios.eliminarSinVerificar(existente.id()));
        if (correo != null) {
            usuarios.findByCorreo(correo)
                    .filter(existente -> existente.estado() == EstadoUsuario.PENDIENTE_VERIFICACION)
                    .ifPresent(existente -> usuarios.eliminarSinVerificar(existente.id()));
        }
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
        VerificacionInfo verificacion = canal == null
                ? new VerificacionInfo(null, null, 0, "PENDIENTE")
                : solicitarCodigo(celular, canal, ipAddress);
        return new RegistroResultado(usuario.id(), usuario.estado().name(), verificacion);
    }

    /** Si el proveedor falla, la cuenta se conserva y se informa el envío fallido (RF-07). */
    private VerificacionInfo solicitarCodigo(String celular, CanalVerificacion canal, String ipAddress) {
        try {
            SolicitarVerificacionPort.EnvioSolicitado envio = solicitarVerificacion.solicitar(
                    celular, PropositoVerificacion.REGISTRO, canal, ipAddress
            );
            return new VerificacionInfo(
                    envio.mensajeId(), envio.canal().name(), envio.reenvioDisponibleEn(), "ENVIADO"
            );
        } catch (DomainException exception) {
            if (!"EXTERNAL_SERVICE_UNAVAILABLE".equals(exception.code())) {
                throw exception;
            }
            Object mensajeId = exception.details().get("mensajeId");
            return new VerificacionInfo(
                    mensajeId == null ? null : UUID.fromString(mensajeId.toString()),
                    canal.name(),
                    0,
                    "FALLIDO"
            );
        }
    }

    private String normalizarCorreo(String correoInput) {
        if (correoInput == null || correoInput.isBlank()) {
            return null;
        }
        Correo correo = Correo.desde(correoInput);
        if (!dominioCorreo.recibeCorreo(correo.dominio())) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "El dominio \"" + correo.dominio() + "\" no recibe correos. Revisa que esté bien escrito.",
                    Map.of("field", "correo")
            );
        }
        return correo.valor();
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