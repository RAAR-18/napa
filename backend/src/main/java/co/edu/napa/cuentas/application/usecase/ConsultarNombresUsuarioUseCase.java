package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class ConsultarNombresUsuarioUseCase implements ConsultarNombresUsuarioPort {
    private final UsuarioRepositoryPort usuarios;

    public ConsultarNombresUsuarioUseCase(UsuarioRepositoryPort usuarios) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    public Map<UUID, String> consultar(Set<UUID> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(usuarios.findNamesByIds(Set.copyOf(usuarioIds)));
    }
}
