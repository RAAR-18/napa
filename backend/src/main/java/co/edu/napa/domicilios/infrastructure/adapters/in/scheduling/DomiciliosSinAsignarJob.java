package co.edu.napa.domicilios.infrastructure.adapters.in.scheduling;

import co.edu.napa.domicilios.application.ports.in.ProcesarDomiciliosSinAsignarPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RF-14 FR-009: revisa periódicamente los domicilios que nadie ha tomado. */
@Component
@ConditionalOnProperty(name = "napa.domicilios.sin-asignar.enabled", havingValue = "true", matchIfMissing = true)
public class DomiciliosSinAsignarJob {
    private static final Logger log = LoggerFactory.getLogger(DomiciliosSinAsignarJob.class);

    private final ProcesarDomiciliosSinAsignarPort procesar;

    public DomiciliosSinAsignarJob(ProcesarDomiciliosSinAsignarPort procesar) {
        this.procesar = procesar;
    }

    @Scheduled(fixedDelayString = "${napa.domicilios.sin-asignar.intervalo-ms:60000}",
            initialDelayString = "${napa.domicilios.sin-asignar.intervalo-ms:60000}")
    public void ejecutar() {
        try {
            var resultado = procesar.procesar();
            if (resultado.avisados() > 0 || resultado.cancelados() > 0) {
                log.info("Domicilios sin asignar: {} avisados, {} cancelados",
                        resultado.avisados(), resultado.cancelados());
            }
        } catch (RuntimeException exception) {
            log.error("No se pudieron procesar los domicilios sin asignar", exception);
        }
    }
}
