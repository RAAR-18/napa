package co.edu.napa.pagos.application.ports.in;

import java.util.List;
import java.util.UUID;

public interface ConsultarMediosPagoPort {
    MediosDisponibles consultar(UUID vendedorId);

    record MediosDisponibles(
            List<String> mediosHabilitados,
            boolean domicilioDisponible,
            boolean vendedorPuntoFijo
    ) {
        public MediosDisponibles {
            mediosHabilitados = List.copyOf(mediosHabilitados);
        }
    }
}
