package co.edu.napa.pedidos.infrastructure.config;

import co.edu.napa.pedidos.application.ports.out.TarifaDomicilioConfiguracionPort;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "napa.pedidos.tarifa")
public record TarifaDomicilioProperties(
        @PositiveOrZero long base,
        @PositiveOrZero long valorPorKm,
        @PositiveOrZero long valorPorMin,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal factorLineaRecta
) implements TarifaDomicilioConfiguracionPort {
}
