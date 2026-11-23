package co.edu.napa.cuentas.infrastructure.adapters.in.scheduling;

import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Borra las cuentas que no se verificaron a tiempo: sin verificar no deben quedar guardadas. */
@Component
public class CuentasSinVerificarJob {
    private static final Logger log = LoggerFactory.getLogger(CuentasSinVerificarJob.class);

    private final UsuarioRepositoryPort usuarios;
    private final Clock clock;
    private final Duration vigencia;

    public CuentasSinVerificarJob(
            UsuarioRepositoryPort usuarios,
            Clock clock,
            @Value("${napa.cuentas.sin-verificar.vigencia-horas:24}") long vigenciaHoras
    ) {
        this.usuarios = usuarios;
        this.clock = clock;
        this.vigencia = Duration.ofHours(vigenciaHoras);
    }

    @Scheduled(fixedDelayString = "${napa.cuentas.sin-verificar.intervalo-ms:3600000}", initialDelay = 60000)
    @Transactional
    public void limpiar() {
        int borradas = usuarios.eliminarSinVerificarCreadasAntesDe(Instant.now(clock).minus(vigencia));
        if (borradas > 0) {
            log.info("Se borraron {} cuentas que no se verificaron a tiempo", borradas);
        }
    }
}
