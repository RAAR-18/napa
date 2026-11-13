package co.edu.napa.domicilios.application.ports.in;

import co.edu.napa.shared.domain.geo.Ubicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Punto A o B tal como lo ve el domiciliario. {@code aproximado = true} significa que la dirección y la referencia se
 * ocultan, las coordenadas se redondean a unos 1,1 km y solo se muestra el barrio (RF-18 FR-004: privacidad del
 * receptor hasta la asignación).
 */
public record PuntoDomicilio(
        double latitud,
        double longitud,
        String direccion,
        String referencia,
        String barrio,
        boolean aproximado
) {
    public static PuntoDomicilio exacto(Ubicacion ubicacion) {
        return new PuntoDomicilio(ubicacion.latitud(), ubicacion.longitud(), ubicacion.direccion(),
                ubicacion.referencia(), ubicacion.barrio(), false);
    }

    public static PuntoDomicilio aproximado(Ubicacion ubicacion) {
        return new PuntoDomicilio(redondear(ubicacion.latitud()), redondear(ubicacion.longitud()), null, null,
                ubicacion.barrio(), true);
    }

    private static double redondear(double grados) {
        return BigDecimal.valueOf(grados).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
