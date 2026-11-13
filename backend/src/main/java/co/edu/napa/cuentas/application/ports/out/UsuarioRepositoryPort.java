package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UsuarioRepositoryPort {
    Usuario save(Usuario usuario);

    Optional<Usuario> findById(UUID id);

    Optional<Usuario> findByCelular(String celular);

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCelular(String celular);

    boolean existsByCorreo(String correo);

    boolean existsByRol(RolUsuario rol);

    Map<UUID, String> findNamesByIds(Set<UUID> ids);
}
