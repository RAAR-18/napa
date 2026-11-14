package co.edu.napa.domicilios.application.ports.in;

import co.edu.napa.shared.domain.geo.GeoPoint;

import java.util.List;
import java.util.UUID;

/** RF-22: el tramo pendiente del domicilio asignado, para dibujarlo en el mapa del domiciliario. */
public interface ConsultarRutaDomicilioPort {
    /** {@code posicion} es opcional: sin ella no hay tramo hacia el origen y el tramo al destino parte de A. */
    Ruta consultarRuta(UUID domiciliarioId, UUID domicilioId, GeoPoint posicion);

    record Ruta(
            UUID domicilioId,
            String estado,
            PuntoDomicilio origen,
            PuntoDomicilio destino,
            List<Tramo> tramos
    ) {}

    /**
     * {@code aproximado = true}: el servicio de rutas no respondió; la distancia es la línea recta por el factor de
     * la tarifa, sin duración ni polilínea.
     */
    record Tramo(
            TipoTramo tipo,
            long distanciaMetros,
            Long duracionSegundos,
            String polilinea,
            boolean aproximado
    ) {}

    enum TipoTramo { HACIA_ORIGEN, HACIA_DESTINO }
}
