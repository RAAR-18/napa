package co.edu.napa.pedidos.infrastructure.config;

import co.edu.napa.pedidos.application.ports.out.DistanciaLineaRectaPort;
import co.edu.napa.pedidos.application.ports.out.TarifaDomicilioConfiguracionPort;
import co.edu.napa.pedidos.application.usecase.CalcularTarifaDomicilioUseCase;
import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.tarifas.application.ports.in.ConsultarTarifaMinimaPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TarifaDomicilioProperties.class)
public class PedidosConfiguration {
    @Bean
    CalcularTarifaDomicilioUseCase calcularTarifaDomicilioUseCase(
            RoutingPort rutas,
            DistanciaLineaRectaPort distancias,
            ConsultarTarifaMinimaPort tarifaMinima,
            TarifaDomicilioConfiguracionPort configuracion
    ) {
        return new CalcularTarifaDomicilioUseCase(rutas, distancias, tarifaMinima, configuracion);
    }
}
