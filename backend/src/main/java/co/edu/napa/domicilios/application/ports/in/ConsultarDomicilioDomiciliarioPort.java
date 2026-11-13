package co.edu.napa.domicilios.application.ports.in;

import java.time.Instant;
import java.util.UUID;

/** RF-18: información de un domicilio para el domiciliario. */
public interface ConsultarDomicilioDomiciliarioPort {
    Detalle consultar(UUID domiciliarioId, UUID domicilioId);

    record Detalle(
            UUID id,
            String estado,
            long ganancia,
            boolean asignadoAMi,
            boolean rutaDisponible,
            long distanciaMetros,
            PuntoDomicilio origen,
            PuntoDomicilio destino,
            Receptor receptor,
            ResumenItems resumenItems,
            Instant asignadoAt
    ) {}

    /** {@code celular} solo viaja al domiciliario asignado. */
    record Receptor(String nombre, String celular) {}

    record ResumenItems(int cantidadItems) {}
}
