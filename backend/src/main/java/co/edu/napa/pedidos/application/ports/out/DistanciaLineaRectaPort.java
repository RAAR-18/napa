package co.edu.napa.pedidos.application.ports.out;

import co.edu.napa.shared.domain.geo.GeoPoint;

import java.math.BigDecimal;

public interface DistanciaLineaRectaPort {
    BigDecimal distanciaMetros(GeoPoint origen, GeoPoint destino);
}
