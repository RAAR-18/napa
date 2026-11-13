package co.edu.napa.domicilios.application;

import java.time.Duration;
import java.util.Objects;

/**
 * Plazos del ciclo del domicilio.
 *
 * @param avisoSinAsignar       tiempo disponible sin domiciliario tras el cual se avisa (RF-14 FR-009: 30 min)
 * @param cancelacionSinAsignar tiempo disponible sin domiciliario tras el cual se cancela (RF-14 FR-009: 2 h)
 * @param margenReserva         antelación con la que se puede publicar una reserva (RF-14 FR-008: 15 min)
 */
public record PlazosDomicilio(Duration avisoSinAsignar, Duration cancelacionSinAsignar, Duration margenReserva) {
    public static final PlazosDomicilio POR_DEFECTO =
            new PlazosDomicilio(Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofMinutes(15));

    public PlazosDomicilio {
        Objects.requireNonNull(avisoSinAsignar, "avisoSinAsignar");
        Objects.requireNonNull(cancelacionSinAsignar, "cancelacionSinAsignar");
        Objects.requireNonNull(margenReserva, "margenReserva");
        if (avisoSinAsignar.isNegative() || avisoSinAsignar.isZero()
                || cancelacionSinAsignar.compareTo(avisoSinAsignar) <= 0 || margenReserva.isNegative()) {
            throw new IllegalArgumentException("El aviso debe ser positivo y anterior a la cancelación.");
        }
    }
}
