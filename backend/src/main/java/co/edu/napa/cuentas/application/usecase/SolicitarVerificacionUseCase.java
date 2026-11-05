package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
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
    private final Clock clock;

    public SolicitarVerificacionUseCase(
            UsuarioRepositoryPort usuarios,
            VerificarCelularPort verificarCelular,
            MensajeRepositoryPort mensajes,
            LimitadorVerificacionPort limitador,
            Clock clock
    ) {
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
        // Los límites cuentan igual exista o no la cuenta, para no revelar cuáles existen.
        limitador.registrarEnvio(celular, ipAddress);
        if (proposito == PropositoVerificacion.CAMBIO && usuarios.existsByCelular(celular)) {
            throw new DomainException("PHONE_ALREADY_REGISTERED", "Ese celular ya está registrado.");
        }
        if (!puedeRecibirCodigo(celular, proposito)) {
            // Misma respuesta que un envío real, sin llamar al proveedor ni gastar un mensaje.
            return new EnvioSolicitado(UUID.randomUUID(), canal, SEGUNDOS_ENTRE_ENVIOS);
        }

        Instant now = Instant.now(clock);
        VerificarCelularPort.EnvioVerificacion envio;
        try {
            envio = verificarCelular.iniciar(celular, proposito, canal);
        } catch (DomainException exception) {
            if (!"EXTERNAL_SERVICE_UNAVAILABLE".equals(exception.code())) {
                throw exception;
            }
            Mensaje fallido = mensajes.save(
                    Mensaje.nuevo(canal, proposito, celular, EstadoMensaje.FALLIDO, null, now)
            );
            throw new DomainException(
                    exception.code(),
                    "No pudimos enviar el código. Inténtalo de nuevo o pídelo por SMS.",
                    Map.of("mensajeId", fallido.id().toString(), "canal", canal.name())
            );
        }
        limitador.reiniciarIntentos(celular, proposito);
        Mensaje enviado = mensajes.save(
                Mensaje.nuevo(canal, proposito, celular, EstadoMensaje.ENVIADO, envio.proveedorId(), now)
        );
        return new EnvioSolicitado(enviado.id(), canal, SEGUNDOS_ENTRE_ENVIOS);
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