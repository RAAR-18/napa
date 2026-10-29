package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.AuthTokens;
import co.edu.napa.cuentas.application.ports.in.ConfirmarRegistroPort;
import co.edu.napa.cuentas.application.ports.out.SesionRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Component
public class ConfirmarRegistroUseCase implements ConfirmarRegistroPort {
    private final UsuarioRepositoryPort usuarios;
    private final SesionRepositoryPort sesiones;
    private final VerificarCelularPort verificarCelular;
    private final TokenServicePort tokenService;
    private final Clock clock;

    public ConfirmarRegistroUseCase(
            UsuarioRepositoryPort usuarios,
            SesionRepositoryPort sesiones,
            VerificarCelularPort verificarCelular,
            TokenServicePort tokenService,
            Clock clock
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public AuthTokens confirmar(String celularInput, String codigo, String deviceId) {
        String celular = Celular.desde(celularInput).valor();
        Sesion.validarDeviceId(deviceId);
        Usuario usuario = usuarios.findByCelular(celular)
                .orElseThrow(() -> new DomainException("VALIDATION_ERROR", "El código no es válido o venció."));
        if (usuario.estado() != EstadoUsuario.PENDIENTE_VERIFICACION) {
            throw new DomainException("CONFLICT", "La cuenta no está pendiente de verificación.");
        }
        VerificacionResultado resultado = verificarCelular.confirmar(
                celular,
                EstadoVerificacion.REGISTRO,
                codigo
        );
        ResultadoOtp.exigirAprobado(resultado);

        Instant now = Instant.now(clock);
        Usuario activado = usuario.activar(now);
        usuarios.save(activado);

        String refreshToken = tokenService.newRefreshToken();
        Sesion sesion = Sesion.nueva(
                activado.id(),
                tokenService.hashRefreshToken(refreshToken),
                deviceId,
                now,
                tokenService.refreshTokenTtl()
        );
        return AuthTokens.desde(tokenService.issue(sesiones.save(sesion), activado, refreshToken), activado);
    }
}