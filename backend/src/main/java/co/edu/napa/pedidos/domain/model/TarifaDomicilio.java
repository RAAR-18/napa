package co.edu.napa.pedidos.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Objects;

public final class TarifaDomicilio {
    private static final BigDecimal METROS_POR_KILOMETRO = BigDecimal.valueOf(1_000);
    private static final BigDecimal SEGUNDOS_POR_MINUTO = BigDecimal.valueOf(60);
    private static final BigDecimal UNIDAD_REDONDEO = BigDecimal.valueOf(100);

    private TarifaDomicilio() {}

    public static long calcularRuta(
            long base,
            long valorPorKm,
            long valorPorMin,
            long distanciaMetros,
            Duration duracion,
            long minima
    ) {
        Objects.requireNonNull(duracion, "duracion");
        if (distanciaMetros < 0 || duracion.isNegative() || valorPorMin < 0) {
            throw new IllegalArgumentException("La distancia, duración y tarifa por minuto no pueden ser negativas.");
        }

        BigDecimal kilometros = BigDecimal.valueOf(distanciaMetros)
                .divide(METROS_POR_KILOMETRO);
        BigDecimal minutos = BigDecimal.valueOf(duracion.toMillis())
                .divide(BigDecimal.valueOf(60_000), 9, RoundingMode.HALF_UP);
        BigDecimal subtotal = calcularSubtotal(base, valorPorKm, kilometros)
                .add(BigDecimal.valueOf(valorPorMin).multiply(minutos));
        return aplicarPisoYRedondeo(subtotal, minima);
    }

    public static long calcularRespaldo(
            long base,
            long valorPorKm,
            BigDecimal distanciaLinealMetros,
            BigDecimal factorLineaRecta,
            long minima
    ) {
        Objects.requireNonNull(distanciaLinealMetros, "distanciaLinealMetros");
        Objects.requireNonNull(factorLineaRecta, "factorLineaRecta");
        if (distanciaLinealMetros.signum() < 0 || factorLineaRecta.signum() <= 0) {
            throw new IllegalArgumentException("La distancia debe ser no negativa y el factor positivo.");
        }

        BigDecimal kilometrosAjustados = distanciaLinealMetros
                .multiply(factorLineaRecta)
                .divide(METROS_POR_KILOMETRO, 12, RoundingMode.HALF_UP);
        return aplicarPisoYRedondeo(calcularSubtotal(base, valorPorKm, kilometrosAjustados), minima);
    }

    private static BigDecimal calcularSubtotal(long base, long valorPorKm, BigDecimal kilometros) {
        if (base < 0 || valorPorKm < 0) {
            throw new IllegalArgumentException("La base y el valor por kilómetro no pueden ser negativos.");
        }
        return BigDecimal.valueOf(base).add(BigDecimal.valueOf(valorPorKm).multiply(kilometros));
    }

    private static long aplicarPisoYRedondeo(BigDecimal subtotal, long minima) {
        if (minima <= 0) {
            throw new IllegalArgumentException("La tarifa mínima debe ser positiva.");
        }
        long redondeada = subtotal.divide(UNIDAD_REDONDEO, 0, RoundingMode.HALF_UP)
                .multiply(UNIDAD_REDONDEO)
                .longValueExact();
        return Math.max(minima, redondeada);
    }
}
