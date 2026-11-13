package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConsultarContactoUsuarioUseCase implements ConsultarContactoUsuarioPort {
    private final UsuarioRepositoryPort usuarios;

    public ConsultarContactoUsuarioUseCase(UsuarioRepositoryPort usuarios) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    public Optional<Contacto> consultar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        return usuarios.findById(usuarioId).map(usuario -> new Contacto(usuario.nombre(), usuario.celular()));
    }
}
