package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.SolicitarVerificacionCelularPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SolicitarVerificacionCelularUseCase implements SolicitarVerificacionCelularPort {
    private final UsuarioRepositoryPort usuarios;
    private final VerificarCelularPort verificarCelular;

    public SolicitarVerificacionCelularUseCase(
            UsuarioRepositoryPort usuarios,
            VerificarCelularPort verificarCelular
    ) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
    }

    @Override
    public void solicitar(String celularInput, EstadoVerificacion proposito, String ipAddress) {
        String celular = Celular.desde(celularInput).valor();
        if (proposito == null) {
            throw new DomainException("VALIDATION_ERROR", "El propósito de la verificación es obligatorio.");
        }
        if (proposito == EstadoVerificacion.REGISTRO) {
            Usuario usuario = usuarios.findByCelular(celular)
                    .orElseThrow(() -> new DomainException("NOT_FOUND", "No se encontró una cuenta pendiente."));
            if (usuario.estado() != EstadoUsuario.PENDIENTE_VERIFICACION) {
                throw new DomainException("CONFLICT", "La cuenta no está pendiente de verificación.");
            }
        }
        verificarCelular.solicitar(celular, proposito, ipAddress);
    }
}