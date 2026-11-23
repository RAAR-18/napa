package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.BuscarCuentaPorCorreoPort;
import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.Correo;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class BuscarCuentaPorCorreoUseCase implements BuscarCuentaPorCorreoPort {
    private final UsuarioRepositoryPort usuarios;

    public BuscarCuentaPorCorreoUseCase(UsuarioRepositoryPort usuarios) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    @Transactional(readOnly = true)
    public String celularDe(String correo) {
        // El registro ya avisa si un correo está en uso, así que esto no revela nada nuevo.
        return usuarios.findByCorreo(Correo.desde(correo).valor())
                .filter(usuario -> usuario.estado() != EstadoUsuario.ELIMINADA)
                .map(Usuario::celular)
                .orElseThrow(() -> new DomainException(
                        "VALIDATION_ERROR",
                        "No encontramos una cuenta con ese correo. Revisa que esté bien escrito."
                ));
    }
}
