package co.edu.napa.cuentas.application.ports.in;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ConsultarNombresUsuarioPort {
    Map<UUID, String> consultar(Set<UUID> usuarioIds);
}
