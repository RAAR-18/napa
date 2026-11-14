package co.edu.napa.cuentas.application.ports.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** RF-08 US2: el celular nuevo espera aquí (no en {@code usuario}) hasta que se confirma el código. */
public interface CambioCelularPendientePort {
    void guardar(UUID usuarioId, String celular, Duration vigencia);

    Optional<String> consultar(UUID usuarioId);

    void borrar(UUID usuarioId);
}
