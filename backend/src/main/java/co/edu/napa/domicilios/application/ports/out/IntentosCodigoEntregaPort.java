package co.edu.napa.domicilios.application.ports.out;

import java.time.Duration;
import java.util.UUID;

/** Fallos del código de entrega por domicilio (RF-24 FR-006). Vive fuera de la transacción de la base. */
public interface IntentosCodigoEntregaPort {
    int MAXIMO_FALLOS = 5;
    Duration BLOQUEO = Duration.ofMinutes(15);

    /** Segundos que faltan para poder intentar de nuevo; 0 si no está bloqueado. */
    long segundosBloqueado(UUID domicilioId);

    /** Cuenta un fallo y devuelve los intentos que quedan; al llegar a 0 empieza el bloqueo. */
    int registrarFallo(UUID domicilioId);

    void limpiar(UUID domicilioId);
}
