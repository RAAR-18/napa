package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.CambiarCelularYFotoPort;
import co.edu.napa.cuentas.application.ports.in.ConfirmarVerificacionPort;
import co.edu.napa.cuentas.application.ports.in.PerfilUsuario;
import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionPort;
import co.edu.napa.cuentas.application.ports.out.AlmacenFotosPort;
import co.edu.napa.cuentas.application.ports.out.CambioCelularPendientePort;
import co.edu.napa.cuentas.application.ports.out.GestionUsuariosRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * RF-08 FR-004: el celular nuevo se guarda aparte 10 minutos y solo reemplaza al anterior cuando el código enviado al
 * número nuevo se confirma. La foto se valida por su contenido (JPEG, PNG o WebP) y tamaño (2 MB).
 */
@Component
public class CambiarCelularYFotoUseCase implements CambiarCelularYFotoPort {
    static final Duration VIGENCIA_CAMBIO = Duration.ofMinutes(10);
    static final int FOTO_MAXIMA = 2 * 1024 * 1024;

    private final SolicitarVerificacionPort solicitar;
    private final ConfirmarVerificacionPort confirmar;
    private final CambioCelularPendientePort pendientes;
    private final GestionUsuariosRepositoryPort usuarios;
    private final AlmacenFotosPort fotos;
    private final UsuarioRepositoryPort cuentas;
    private final Clock clock;

    public CambiarCelularYFotoUseCase(
            SolicitarVerificacionPort solicitar,
            ConfirmarVerificacionPort confirmar,
            CambioCelularPendientePort pendientes,
            GestionUsuariosRepositoryPort usuarios,
            AlmacenFotosPort fotos,
            UsuarioRepositoryPort cuentas,
            Clock clock
    ) {
        this.solicitar = Objects.requireNonNull(solicitar, "solicitar");
        this.confirmar = Objects.requireNonNull(confirmar, "confirmar");
        this.pendientes = Objects.requireNonNull(pendientes, "pendientes");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.fotos = Objects.requireNonNull(fotos, "fotos");
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SolicitarVerificacionPort.EnvioSolicitado solicitarCambioCelular(
            UUID usuarioId, String rol, String celular, CanalVerificacion canal, String ipAddress) {
        noAdministrador(rol);
        String nuevo = ReglasPerfil.celular(celular);
        PerfilUsuario actual = perfil(usuarioId);
        if (nuevo.equals(actual.celular())) {
            throw ReglasPerfil.invalido("celular", "Ese ya es tu celular.");
        }
        // Antes que los límites de envío: un número de otra cuenta no debe gastar intentos.
        if (cuentas.existsByCelular(nuevo)) {
            throw new DomainException("PHONE_ALREADY_REGISTERED", "Ese celular ya está registrado.");
        }
        // Aplica los límites de envío de RF-70.
        SolicitarVerificacionPort.EnvioSolicitado envio = solicitar.solicitar(nuevo, PropositoVerificacion.CAMBIO,
                canal == null ? CanalVerificacion.WHATSAPP : canal, ipAddress);
        pendientes.guardar(usuarioId, nuevo, VIGENCIA_CAMBIO);
        return envio;
    }

    @Override
    @Transactional
    public PerfilUsuario confirmarCambioCelular(UUID usuarioId, String rol, String codigo) {
        noAdministrador(rol);
        if (codigo == null || !codigo.matches("[0-9]{6}")) {
            throw ReglasPerfil.invalido("codigo", "El código tiene 6 dígitos.");
        }
        String nuevo = pendientes.consultar(usuarioId)
                .orElseThrow(() -> new DomainException("OTP_EXPIRED", "El código venció; solicita uno nuevo."));
        confirmar.confirmar(nuevo, PropositoVerificacion.CAMBIO, codigo);
        usuarios.bloquear(usuarioId)
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "Debes iniciar sesión para continuar."));
        usuarios.actualizarCelular(usuarioId, nuevo, clock.instant());
        pendientes.borrar(usuarioId);
        return perfil(usuarioId);
    }

    @Override
    @Transactional
    public String cambiarFoto(UUID usuarioId, byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw ReglasPerfil.invalido("foto", "Adjunta una foto.");
        }
        if (contenido.length > FOTO_MAXIMA) {
            throw ReglasPerfil.invalido("foto", "La foto no puede pesar más de 2 MB.");
        }
        String extension = extension(contenido);
        perfil(usuarioId);
        String url = fotos.guardar(usuarioId, contenido, extension);
        usuarios.actualizarFoto(usuarioId, url, clock.instant());
        return url;
    }

    /** El tipo real por los primeros bytes, no por el nombre del archivo. */
    static String extension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        throw ReglasPerfil.invalido("foto", "La foto debe ser JPEG, PNG o WebP.");
    }

    private PerfilUsuario perfil(UUID usuarioId) {
        return usuarios.perfil(Objects.requireNonNull(usuarioId, "usuarioId"))
                .orElseThrow(() -> new DomainException("UNAUTHORIZED", "Debes iniciar sesión para continuar."));
    }

    private static void noAdministrador(String rol) {
        if ("ADMINISTRADOR".equals(rol)) {
            throw new DomainException("FORBIDDEN", "El administrador no cambia su celular aquí.");
        }
    }
}
