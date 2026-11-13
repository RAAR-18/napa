package co.edu.napa.pagos.infrastructure.config;

import co.edu.napa.pagos.application.ports.out.ConfiguracionPagoRepositoryPort;
import co.edu.napa.pagos.application.ports.out.ConsultarTipoVendedorPort;
import co.edu.napa.pagos.application.ports.out.PagoRepositoryPort;
import co.edu.napa.pagos.application.usecase.ConfigurarPagoVendedorUseCase;
import co.edu.napa.pagos.application.usecase.ConsultarConfiguracionPagoUseCase;
import co.edu.napa.pagos.application.usecase.ConsultarMediosPagoUseCase;
import co.edu.napa.pagos.application.usecase.ConfirmarPagoPedidoUseCase;
import co.edu.napa.pagos.application.usecase.RegistrarPagoPedidoPendienteUseCase;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

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

    @Bean
    RegistrarPagoPedidoPendienteUseCase registrarPagoPedidoPendienteUseCase(
            PagoRepositoryPort pagos,
            Clock clock
    ) {
        return new RegistrarPagoPedidoPendienteUseCase(pagos, clock);
    }

    @Bean
    ConfirmarPagoPedidoUseCase confirmarPagoPedidoUseCase(
            PagoRepositoryPort pagos,
            EventPublisherPort eventos,
            Clock clock
    ) {
        return new ConfirmarPagoPedidoUseCase(pagos, eventos, clock);
    }
}
