package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.SolicitarRecuperacionPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.Celular;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.RolUsuario;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SolicitarRecuperacionUseCase implements SolicitarRecuperacionPort {
    private final UsuarioRepositoryPort usuarios;
    private final VerificarCelularPort verificarCelular;

    public SolicitarRecuperacionUseCase(UsuarioRepositoryPort usuarios, VerificarCelularPort verificarCelular) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.verificarCelular = Objects.requireNonNull(verificarCelular, "verificarCelular");
    }

    @Override
    public void solicitar(String celularInput, String ipAddress) {
        String celular = Celular.desde(celularInput).valor();
        // Solo se envía código si la cuenta existe, está activa y no es de administrador. Quien llama
        // recibe la misma respuesta en todos los casos, y no se gasta un mensaje en números ajenos.
        usuarios.findByCelular(celular)
                .filter(usuario -> usuario.estado() == EstadoUsuario.ACTIVA)
                .filter(usuario -> usuario.rol() != RolUsuario.ADMINISTRADOR)
                .ifPresent(usuario ->
                        verificarCelular.solicitar(celular, EstadoVerificacion.RECUPERACION_CONTRASENA, ipAddress));
    }
}