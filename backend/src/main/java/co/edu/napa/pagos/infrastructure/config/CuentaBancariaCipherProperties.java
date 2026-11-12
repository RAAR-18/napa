package co.edu.napa.pagos.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "napa.pagos.cifrado")
public record CuentaBancariaCipherProperties(@NotBlank String claveBase64) {
}
