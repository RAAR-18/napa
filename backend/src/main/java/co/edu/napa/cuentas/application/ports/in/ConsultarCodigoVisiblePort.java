package co.edu.napa.cuentas.application.ports.in;

import java.util.Optional;
import java.util.UUID;

/** Código de registro que se muestra como imagen en la app mientras no haya SMS real. */
public interface ConsultarCodigoVisiblePort {
    Optional<String> consultar(UUID mensajeId);
}
