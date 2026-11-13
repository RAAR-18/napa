package co.edu.napa.productos;

import co.edu.napa.productos.application.ports.out.PedidosPendientesProductoPort;
import co.edu.napa.productos.infrastructure.config.PedidosProductoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PedidosProductoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PedidosProductoConfiguration.class, ConsumerConfiguration.class);

    @Test void noOrdersExistWhileTheModuleIsAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PedidosPendientesProductoPort.class).contar(UUID.randomUUID())).isZero();
        });
    }
    @Test void enablingOrdersRequiresAnActualPendingOrdersAdapter() {
        runner.withPropertyValues("napa.pedidos.enabled=true").run(context -> assertThat(context).hasFailed());
    }
    @Test void actualAdapterReplacesTheNoOrdersImplementation() {
        runner.withPropertyValues("napa.pedidos.enabled=true")
                .withBean(PedidosPendientesProductoPort.class, () -> id -> 3)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PedidosPendientesProductoPort.class);
                    assertThat(context.getBean(PedidosPendientesProductoPort.class).contar(UUID.randomUUID())).isEqualTo(3);
                });
    }
    @Configuration(proxyBeanMethods = false)
    static class ConsumerConfiguration {
        @Bean Object consumer(PedidosPendientesProductoPort port) { return new Object(); }
    }
}
