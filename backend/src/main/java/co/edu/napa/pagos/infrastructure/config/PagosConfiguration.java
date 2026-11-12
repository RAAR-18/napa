package co.edu.napa.pagos.infrastructure.config;

import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.ConsultarTipoVendedorPort;
import co.edu.napa.pagos.application.usecase.ConfigurarPagoVendedorUseCase;
import co.edu.napa.pagos.application.usecase.ConsultarConfiguracionPagoUseCase;
import co.edu.napa.pagos.application.usecase.ConsultarMediosPagoUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CuentaBancariaCipherProperties.class)
public class PagosConfiguration {
    @Bean
    ConfigurarPagoVendedorUseCase configurarPagoVendedorUseCase(
            ConfiguracionPagoRepositoryPort configuraciones
    ) {
        return new ConfigurarPagoVendedorUseCase(configuraciones);
    }

    @Bean
    ConsultarConfiguracionPagoUseCase consultarConfiguracionPagoUseCase(
            ConfiguracionPagoRepositoryPort configuraciones
    ) {
        return new ConsultarConfiguracionPagoUseCase(configuraciones);
    }

    @Bean
    ConsultarMediosPagoUseCase consultarMediosPagoUseCase(
            ConfiguracionPagoRepositoryPort configuraciones,
            ConsultarTipoVendedorPort vendedores
    ) {
        return new ConsultarMediosPagoUseCase(configuraciones, vendedores);
    }
}
