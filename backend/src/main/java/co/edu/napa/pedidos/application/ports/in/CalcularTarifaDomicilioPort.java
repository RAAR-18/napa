package co.edu.napa.pedidos.application.ports.in;

import co.edu.napa.shared.domain.geo.GeoPoint;

public interface CalcularTarifaDomicilioPort {
    long calcular(GeoPoint origen, GeoPoint destino);
}
