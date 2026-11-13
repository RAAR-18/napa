package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarPerfilPublicoUsuarioPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConsultarPerfilPublicoUsuarioUseCase implements ConsultarPerfilPublicoUsuarioPort {
    private final UsuarioRepositoryPort usuarios;

    public ConsultarPerfilPublicoUsuarioUseCase(UsuarioRepositoryPort usuarios) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    public Optional<PerfilPublico> consultarPerfil(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        return usuarios.findById(usuarioId).map(usuario -> new PerfilPublico(usuario.nombre(), usuario.fotoUrl(),
                usuario.rol().name(), usuario.createdAt()));
    }
}
