package co.edu.napa.tarifas.infrastructure.config;

import co.edu.napa.tarifas.application.ports.out.TarifaMinimaDefectoPort;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "napa.tarifa")
public record TarifaProperties(@Positive long minimaDefecto) implements TarifaMinimaDefectoPort {
    @Override
    public long valor() {
        return minimaDefecto;
    }
}
