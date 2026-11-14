package co.edu.napa.domicilios.application.ports.out;

import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.shared.domain.geo.Ubicacion;

/** Domicilio con sus puntos A y B, leídos en la misma consulta para el listado del administrador (RF-12). */
public record DomicilioSupervisionProjection(Domicilio domicilio, Ubicacion origen, Ubicacion destino) {}
