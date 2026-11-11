package co.edu.napa.productos.infrastructure.config;

import co.edu.napa.productos.application.ports.out.PedidosPendientesProductoPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PedidosProductoConfiguration {
    // El proyecto todavía no tiene módulo ni tablas de pedidos. Al habilitarlo se exige un adaptador real.
    @Bean
    @ConditionalOnMissingBean(PedidosPendientesProductoPort.class)
    @ConditionalOnProperty(name = "napa.pedidos.enabled", havingValue = "false", matchIfMissing = true)
    PedidosPendientesProductoPort sinModuloPedidos() {
        return productoId -> 0;
    }
}
