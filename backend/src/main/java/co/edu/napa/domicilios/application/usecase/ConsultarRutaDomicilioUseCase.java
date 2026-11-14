package co.edu.napa.domicilios.application.usecase;

import co.edu.napa.domicilios.application.ports.in.ConsultarRutaDomicilioPort;
import co.edu.napa.domicilios.application.ports.in.PuntoDomicilio;
import co.edu.napa.domicilios.application.ports.out.DomicilioRepositoryPort;
import co.edu.napa.domicilios.domain.model.Domicilio;
import co.edu.napa.domicilios.domain.model.EstadoDomicilio;
import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.shared.application.ports.out.UbicacionRepositoryPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.Ubicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * RF-22. Sin {@code @Transactional}: las llamadas al servicio de rutas pueden tardar y no deben retener una conexión
 * de la base. Los dos tramos de {@code ASIGNADO} se piden a la vez. Si el servicio de rutas falla, el tramo se
 * aproxima igual que la tarifa (línea recta × {@code napa.pedidos.tarifa.factor-linea-recta}).
 */
public class ConsultarRutaDomicilioUseCase implements ConsultarRutaDomicilioPort {
    private static final double RADIO_TIERRA_METROS = 6_371_008.8;

    private final DomicilioRepositoryPort domicilios;
    private final UbicacionRepositoryPort ubicaciones;
    private final RoutingPort rutas;
    private final BigDecimal factorLineaRecta;
    private final Executor executor;

    public ConsultarRutaDomicilioUseCase(
            DomicilioRepositoryPort domicilios,
            UbicacionRepositoryPort ubicaciones,
            RoutingPort rutas,
            BigDecimal factorLineaRecta,
            Executor executor
    ) {
        this.domicilios = Objects.requireNonNull(domicilios, "domicilios");
        this.ubicaciones = Objects.requireNonNull(ubicaciones, "ubicaciones");
        this.rutas = Objects.requireNonNull(rutas, "rutas");
        this.factorLineaRecta = Objects.requireNonNull(factorLineaRecta, "factorLineaRecta");
        if (factorLineaRecta.signum() <= 0) {
            throw new IllegalArgumentException("factorLineaRecta must be positive");
        }
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Ruta consultarRuta(UUID domiciliarioId, UUID domicilioId, GeoPoint posicion) {
        Objects.requireNonNull(domiciliarioId, "domiciliarioId");
        Domicilio domicilio = domicilios.buscar(Objects.requireNonNull(domicilioId, "domicilioId"))
                .orElseThrow(() -> new DomainException("NOT_FOUND", "El domicilio no está disponible."));
        if (!domicilio.asignadoA(domiciliarioId)) {
            throw new DomainException("FORBIDDEN", "No tienes acceso a este domicilio.");
        }
        if (!Domicilio.EN_CURSO.contains(domicilio.estado())) {
            throw new DomainException("INVALID_STATE", "Este domicilio ya terminó.",
                    Map.of("estadoActual", domicilio.estado().name()));
        }
        Ubicacion origen = ubicacion(domicilio.ubicacionOrigenId());
        Ubicacion destino = ubicacion(domicilio.ubicacionDestinoId());
        GeoPoint a = punto(origen);
        GeoPoint b = punto(destino);

        List<CompletableFuture<Tramo>> pendientes = new ArrayList<>();
        if (domicilio.estado() == EstadoDomicilio.ASIGNADO) {
            if (posicion != null) {
                pendientes.add(tramo(TipoTramo.HACIA_ORIGEN, posicion, a));
            }
            pendientes.add(tramo(TipoTramo.HACIA_DESTINO, a, b));
        } else {
            pendientes.add(tramo(TipoTramo.HACIA_DESTINO, posicion == null ? a : posicion, b));
        }
        List<Tramo> tramos;
        try {
            tramos = pendientes.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        }
        return new Ruta(domicilio.id(), domicilio.estado().name(), PuntoDomicilio.exacto(origen),
                PuntoDomicilio.exacto(destino), tramos);
    }

    private CompletableFuture<Tramo> tramo(TipoTramo tipo, GeoPoint desde, GeoPoint hasta) {
        return CompletableFuture.supplyAsync(() -> calcular(tipo, desde, hasta), executor);
    }

    private Tramo calcular(TipoTramo tipo, GeoPoint desde, GeoPoint hasta) {
        try {
            var ruta = rutas.computeRoute(desde, hasta);
            return new Tramo(tipo, ruta.distanceMeters(), ruta.duration().toSeconds(), ruta.encodedPolyline(), false);
        } catch (DomainException exception) {
            if (!"EXTERNAL_SERVICE_UNAVAILABLE".equals(exception.code())) {
                throw exception;
            }
            long aproximada = BigDecimal.valueOf(lineaRectaMetros(desde, hasta))
                    .multiply(factorLineaRecta)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            return new Tramo(tipo, aproximada, null, null, true);
        }
    }

    /** Haversine; la diferencia con la distancia geodésica de PostGIS es despreciable a escala de ciudad. */
    static double lineaRectaMetros(GeoPoint desde, GeoPoint hasta) {
        double lat1 = Math.toRadians(desde.latitude());
        double lat2 = Math.toRadians(hasta.latitude());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(hasta.longitude() - desde.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private Ubicacion ubicacion(UUID id) {
        return ubicaciones.findById(id)
                .orElseThrow(() -> new DomainException("INVALID_STATE", "El domicilio no tiene una ubicación válida."));
    }

    private static GeoPoint punto(Ubicacion ubicacion) {
        return new GeoPoint(ubicacion.latitud(), ubicacion.longitud());
    }
}
