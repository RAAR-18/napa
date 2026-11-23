package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.EsperaReenvioPort;
import co.edu.napa.cuentas.application.ports.out.LimitadorVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.MensajeRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.EstadoMensaje;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.Mensaje;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * No es @Transactional a propósito: el registro lo llama dentro de su transacción y, si el proveedor
 * falla, captura la excepción y conserva la cuenta. Una excepción que cruzara un límite transaccional
 * marcaría toda la transacción para deshacerse.
 */
@Component
public class SolicitarVerificacionUseCase implements SolicitarVerificacionPort {
    static final long SEGUNDOS_ENTRE_ENVIOS = 60;

    private final UsuarioRepositoryPort usuarios;
    private final VerificarCelularPort verificarCelular;
    private final MensajeRepositoryPort mensajes;
    private final LimitadorVerificacionPort limitador;
    private final EsperaReenvioPort espera;
    private final Clock clock;

    public SolicitarVerificacionUseCase(
            UsuarioRepositoryPort usuarios,
            VerificarCelularPort verificarCelular,
            MensajeRepositoryPort mensajes,
            LimitadorVerificacionPort limitador,
            EsperaReenvioPort espera,
            Clock clock
    ) {
        this.espera = Objects.requireNonNull(espera, "espera");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.limitador = Objects.requireNonNull(limitador, "limitador");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EnvioSolicitado solicitar(
            String celularInput,
            PropositoVerificacion proposito,
            CanalVerificacion canal,
            String ipAddress
    ) {
        String celular = Celular.desde(celularInput).valor();
        if (proposito == null || canal == null) {
            throw new DomainException("VALIDATION_ERROR", "El propósito y el canal son obligatorios.");
        }
        if (proposito == PropositoVerificacion.CAMBIO && canal == CanalVerificacion.CORREO) {
            // El código prueba que la persona tiene el celular nuevo; un correo no lo demuestra.
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "Para cambiar de celular el código debe llegar por SMS al número nuevo.",
                    Map.of("field", "canal")
            );
        }
        // La espera y los límites cuentan igual exista o no la cuenta, para no revelar cuáles existen.
        espera.exigirDisponible(celular);
        limitador.registrarEnvio(celular, ipAddress);
        try {
            return enviar(celular, proposito, canal);
        } catch (RuntimeException exception) {
            // Si el código no salió no cuenta: la persona puede pedirlo enseguida por el otro medio.
            limitador.liberarEnvio(celular, ipAddress);
            throw exception;
        }
    }

    private EnvioSolicitado enviar(String celular, PropositoVerificacion proposito, CanalVerificacion canal) {
        if (proposito == PropositoVerificacion.CAMBIO && usuarios.existsByCelular(celular)) {
            throw new DomainException("PHONE_ALREADY_REGISTERED", "Ese celular ya está registrado.");
        }
        if (!puedeRecibirCodigo(celular, proposito)) {
            // Misma respuesta que un envío real, sin llamar al proveedor ni gastar un mensaje.
            return new EnvioSolicitado(UUID.randomUUID(), canal, espera.registrarEnvio(celular));
        }

        String correo = null;
        if (canal == CanalVerificacion.CORREO) {
            correo = usuarios.findByCelular(celular).map(Usuario::correo).orElse(null);
            if (correo == null || correo.isBlank()) {
                throw new DomainException(
                        "VALIDATION_ERROR",
                        "Tu cuenta no tiene correo registrado. Elige recibir el código por SMS.",
                        Map.of("field", "canal")
                );
            }
        }

        Instant now = Instant.now(clock);
        VerificarCelularPort.EnvioVerificacion envio;
        try {
            envio = correo == null
                    ? verificarCelular.iniciar(celular, proposito, canal)
                    : verificarCelular.iniciar(celular, proposito, canal, correo);
        } catch (DomainException exception) {
            if (!"EXTERNAL_SERVICE_UNAVAILABLE".equals(exception.code())) {
                throw exception;
            }
            Mensaje fallido = mensajes.save(
                    Mensaje.nuevo(canal, proposito, celular, EstadoMensaje.FALLIDO, null, now)
            );
            throw new DomainException(
                    exception.code(),
                    canal == CanalVerificacion.CORREO
                            ? "No pudimos enviar el código a tu correo. Inténtalo de nuevo o pídelo por SMS."
                            : "No pudimos enviar el SMS. Inténtalo de nuevo o pide el código por correo.",
                    Map.of("mensajeId", fallido.id().toString(), "canal", canal.name())
            );
        }
        limitador.reiniciarIntentos(celular, proposito);
        Mensaje enviado = mensajes.save(
                Mensaje.nuevo(canal, proposito, celular, EstadoMensaje.ENVIADO, envio.proveedorId(), now)
        );
        return new EnvioSolicitado(enviado.id(), canal, espera.registrarEnvio(celular));
    }

    private boolean puedeRecibirCodigo(String celular, PropositoVerificacion proposito) {
        return switch (proposito) {
            case REGISTRO -> usuarios.findByCelular(celular)
                    .filter(usuario -> usuario.estado() == EstadoUsuario.PENDIENTE_VERIFICACION)
                    .isPresent();
            case RECUPERACION -> usuarios.findByCelular(celular)
                    .filter(usuario -> usuario.estado() == EstadoUsuario.ACTIVA)
                    .filter(usuario -> usuario.rol() != RolUsuario.ADMINISTRADOR)
                    .isPresent();
            case CAMBIO -> true;
        };
    }
}