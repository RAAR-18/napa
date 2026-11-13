package co.edu.napa.emprendimientos.domain.model;

import java.time.LocalTime;
import java.util.Objects;

public final class HorarioAtencion {
    private HorarioAtencion() {
    }

    public static boolean estaAbierto(LocalTime apertura, LocalTime cierre, LocalTime horaActual) {
        Objects.requireNonNull(apertura, "apertura");
        Objects.requireNonNull(cierre, "cierre");
        Objects.requireNonNull(horaActual, "horaActual");
        if (apertura.equals(cierre)) {
            return false;
        }
        if (apertura.isBefore(cierre)) {
            return !horaActual.isBefore(apertura) && horaActual.isBefore(cierre);
        }
        return !horaActual.isBefore(apertura) || horaActual.isBefore(cierre);
    }
}
