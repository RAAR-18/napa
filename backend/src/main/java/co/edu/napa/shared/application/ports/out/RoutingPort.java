package co.edu.napa.shared.application.ports.out;

import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.RouteResult;

public interface RoutingPort {
    RouteResult computeRoute(GeoPoint origin, GeoPoint destination);
}
