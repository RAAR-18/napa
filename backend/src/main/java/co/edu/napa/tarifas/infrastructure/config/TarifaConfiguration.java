package co.edu.napa.tarifas.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TarifaProperties.class)
public class TarifaConfiguration {
}
