package co.edu.napa.pedidos.application.usecase;

import co.edu.napa.pedidos.application.ports.in.CalcularTarifaDomicilioPort;
import co.edu.napa.pedidos.application.ports.out.DistanciaLineaRectaPort;
import co.edu.napa.pedidos.application.ports.out.TarifaDomicilioConfiguracionPort;
import co.edu.napa.pedidos.domain.model.TarifaDomicilio;
import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.tarifas.application.ports.in.ConsultarTarifaMinimaPort;

import java.util.Objects;

public class CalcularTarifaDomicilioUseCase implements CalcularTarifaDomicilioPort {
    private final RoutingPort rutas;
    private final DistanciaLineaRectaPort distancias;
    private final ConsultarTarifaMinimaPort tarifaMinima;
    private final TarifaDomicilioConfiguracionPort configuracion;

    public CalcularTarifaDomicilioUseCase(
            RoutingPort rutas,
            DistanciaLineaRectaPort distancias,
            ConsultarTarifaMinimaPort tarifaMinima,
            TarifaDomicilioConfiguracionPort configuracion
    ) {
        this.rutas = Objects.requireNonNull(rutas, "rutas");
        this.distancias = Objects.requireNonNull(distancias, "distancias");
        this.tarifaMinima = Objects.requireNonNull(tarifaMinima, "tarifaMinima");
        this.configuracion = Objects.requireNonNull(configuracion, "configuracion");
    }

    @Override
    public long calcular(GeoPoint origen, GeoPoint destino) {
        Objects.requireNonNull(origen, "origen");
        Objects.requireNonNull(destino, "destino");

        long minima = tarifaMinima.consultar().valor();
        try {
            var ruta = rutas.computeRoute(origen, destino);
            return TarifaDomicilio.calcularRuta(
                    configuracion.base(),
                    configuracion.valorPorKm(),
                    configuracion.valorPorMin(),
                    ruta.distanceMeters(),
                    ruta.duration(),
                    minima
            );
        } catch (DomainException exception) {
            if (!"EXTERNAL_SERVICE_UNAVAILABLE".equals(exception.code())) {
                throw exception;
            }
            return TarifaDomicilio.calcularRespaldo(
                    configuracion.base(),
                    configuracion.valorPorKm(),
                    distancias.distanciaMetros(origen, destino),
                    configuracion.factorLineaRecta(),
                    minima
            );
        }
    }
}
