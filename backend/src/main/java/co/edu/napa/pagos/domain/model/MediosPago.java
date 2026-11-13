package co.edu.napa.pagos.domain.model;

import java.util.List;
import java.util.Objects;

public record MediosPago(
        List<MedioPago> mediosHabilitados,
        boolean domicilioDisponible,
        boolean vendedorPuntoFijo
) {
    public MediosPago {
        mediosHabilitados = List.copyOf(Objects.requireNonNull(mediosHabilitados, "mediosHabilitados"));
    }
}
