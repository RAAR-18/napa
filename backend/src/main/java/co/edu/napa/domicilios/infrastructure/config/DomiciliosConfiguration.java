package co.edu.napa.domicilios.infrastructure.config;

import co.edu.napa.cuentas.application.ports.in.ConsultarContactoUsuarioPort;
import co.edu.napa.cuentas.application.ports.in.ConsultarNombresUsuarioPort;
import co.edu.napa.domicilios.application.PlazosDomicilio;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.application.ports.out.IntentosCodigoEntregaPort;
import co.edu.napa.domicilios.application.usecase.CancelarDomicilioAdminUseCase;
import co.edu.napa.domicilios.application.usecase.ConsultarDomicilioDePedidoUseCase;
import co.edu.napa.domicilios.application.usecase.ConsultarRutaDomicilioUseCase;
import co.edu.napa.domicilios.application.usecase.DomiciliarioUseCase;
import co.edu.napa.domicilios.application.usecase.EjecucionDomicilioUseCase;
import co.edu.napa.domicilios.application.usecase.ListarDomiciliosVendedorUseCase;
import co.edu.napa.domicilios.application.usecase.PagoTarifaUseCase;
import co.edu.napa.domicilios.application.usecase.ProcesarDomiciliosSinAsignarUseCase;
import co.edu.napa.domicilios.application.usecase.PublicarDomicilioUseCase;
import co.edu.napa.domicilios.application.usecase.SupervisarDomiciliosUseCase;
import co.edu.napa.emprendimientos.application.ports.in.ConsultarEmprendimientoParaPedidoPort;
import co.edu.napa.pagos.application.ports.in.ConsultarEstadoPagoPedidoPort;
import co.edu.napa.pagos.application.ports.in.PagoTarifaDomicilioPort;
import co.edu.napa.pedidos.application.ports.in.PedidoParaDomicilioPort;
import co.edu.napa.shared.application.ports.out.EventPublisherPort;
import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DomiciliosConfiguration.PlazosProperties.class)
public class DomiciliosConfiguration {

    /** {@code napa.domicilios.plazos.*}; los valores por defecto son los de RF-14. */
    @ConfigurationProperties("napa.domicilios.plazos")
    public record PlazosProperties(Duration avisoSinAsignar, Duration cancelacionSinAsignar, Duration margenReserva) {
        PlazosDomicilio toPlazos() {
            PlazosDomicilio defecto = PlazosDomicilio.POR_DEFECTO;
            return new PlazosDomicilio(
                    avisoSinAsignar == null ? defecto.avisoSinAsignar() : avisoSinAsignar,
                    cancelacionSinAsignar == null ? defecto.cancelacionSinAsignar() : cancelacionSinAsignar,
                    margenReserva == null ? defecto.margenReserva() : margenReserva);
        }
    }

    @Bean
    PlazosDomicilio plazosDomicilio(PlazosProperties properties) {
        return properties.toPlazos();
    }

    @Bean
    PublicarDomicilioUseCase publicarDomicilioUseCase(
            PedidoParaDomicilioPort pedidos,
            ConsultarEmprendimientoParaPedidoPort emprendimientos,
            ConsultarEstadoPagoPedidoPort pagos,
            UbicacionRepositoryPort ubicaciones,
            DomicilioRepositoryPort domicilios,
            EventPublisherPort eventos,
            PlazosDomicilio plazos,
            Clock clock
    ) {
        return new PublicarDomicilioUseCase(pedidos, emprendimientos, pagos, ubicaciones, domicilios, eventos,
                plazos, clock);
    }

    @Bean
    ListarDomiciliosVendedorUseCase listarDomiciliosVendedorUseCase(
            DomicilioRepositoryPort domicilios,
            ConsultarNombresUsuarioPort usuarios
    ) {
        return new ListarDomiciliosVendedorUseCase(domicilios, usuarios);
    }

    @Bean
    DomiciliarioUseCase domiciliarioUseCase(
            DomicilioRepositoryPort domicilios,
            UbicacionRepositoryPort ubicaciones,
            PedidoParaDomicilioPort pedidos,
            ConsultarContactoUsuarioPort usuarios,
            EventPublisherPort eventos,
            Clock clock,
            @Value("${napa.domicilios.radio-disponibles-metros:" + DomiciliarioUseCase.RADIO_POR_DEFECTO_METROS + "}")
            long radioMetros
    ) {
        return new DomiciliarioUseCase(domicilios, ubicaciones, pedidos, usuarios, eventos, clock, radioMetros);
    }

    @Bean
    ConsultarDomicilioDePedidoUseCase consultarDomicilioDePedidoUseCase(DomicilioRepositoryPort domicilios) {
        return new ConsultarDomicilioDePedidoUseCase(domicilios);
    }

    @Bean
    ProcesarDomiciliosSinAsignarUseCase procesarDomiciliosSinAsignarUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            EventPublisherPort eventos,
            PlazosDomicilio plazos,
            Clock clock
    ) {
        return new ProcesarDomiciliosSinAsignarUseCase(domicilios, pedidos, eventos, plazos, clock);
    }

    @Bean
    EjecucionDomicilioUseCase ejecucionDomicilioUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            IntentosCodigoEntregaPort intentos,
            EventPublisherPort eventos,
            Clock clock
    ) {
        return new EjecucionDomicilioUseCase(domicilios, pedidos, intentos, eventos, clock);
    }

    /** RF-22: el respaldo usa el mismo factor de línea recta que la tarifa, para que mapa y tarifa degraden igual. */
    @Bean
    ConsultarRutaDomicilioUseCase consultarRutaDomicilioUseCase(
            DomicilioRepositoryPort domicilios,
            UbicacionRepositoryPort ubicaciones,
            RoutingPort rutas,
            @Value("${napa.pedidos.tarifa.factor-linea-recta:1.3}") BigDecimal factorLineaRecta
    ) {
        return new ConsultarRutaDomicilioUseCase(domicilios, ubicaciones, rutas, factorLineaRecta,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    PagoTarifaUseCase pagoTarifaUseCase(DomicilioRepositoryPort domicilios, PagoTarifaDomicilioPort pagos) {
        return new PagoTarifaUseCase(domicilios, pagos);
    }

    @Bean
    SupervisarDomiciliosUseCase supervisarDomiciliosUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            PagoTarifaDomicilioPort pagos,
            ConsultarNombresUsuarioPort usuarios,
            Clock clock
    ) {
        return new SupervisarDomiciliosUseCase(domicilios, pedidos, pagos, usuarios, clock);
    }

    @Bean
    CancelarDomicilioAdminUseCase cancelarDomicilioAdminUseCase(
            DomicilioRepositoryPort domicilios,
            PedidoParaDomicilioPort pedidos,
            IntentosCodigoEntregaPort intentos,
            EventPublisherPort eventos,
            Clock clock
    ) {
        return new CancelarDomicilioAdminUseCase(domicilios, pedidos, intentos, eventos, clock);
    }
}
