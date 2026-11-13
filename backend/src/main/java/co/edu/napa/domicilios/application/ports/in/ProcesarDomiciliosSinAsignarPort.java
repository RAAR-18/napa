package co.edu.napa.domicilios.application.ports.in;

/** RF-14 FR-009: avisa a los 30 min y cancela a las 2 h los domicilios que nadie toma. */
public interface ProcesarDomiciliosSinAsignarPort {
    Resultado procesar();

    record Resultado(int avisados, int cancelados) {}
}
