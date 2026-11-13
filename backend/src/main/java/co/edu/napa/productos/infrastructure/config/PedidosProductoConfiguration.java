package co.edu.napa.productos.infrastructure.config;

import co.edu.napa.productos.application.ports.out.PedidosPendientesProductoPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PedidosProductoConfiguration {
    // Solo se permite el contador simulado en entornos donde la persistencia de pedidos está desactivada.
    @Bean
    @ConditionalOnMissingBean(PedidosPendientesProductoPort.class)
    @ConditionalOnProperty(name = "napa.pedidos.enabled", havingValue = "false", matchIfMissing = true)
    PedidosPendientesProductoPort sinModuloPedidos() {
        return productoId -> 0;
    }
}
