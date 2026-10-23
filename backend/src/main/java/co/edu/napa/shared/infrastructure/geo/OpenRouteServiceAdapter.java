package co.edu.napa.shared.infrastructure.geo;

import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.RouteResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Calcula rutas con OpenRouteService (perfil driving-car).
 * Plan gratuito Standard: 2.000 solicitudes/día y 40/min para Directions (verificado 2026-10-22).
 * Si se autoaloja ORS, solo cambia napa.routing.ors.base-url.
 */
@Component
public class OpenRouteServiceAdapter implements RoutingPort {
    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceAdapter.class);
    private static final String DIRECTIONS_PATH = "/v2/directions/driving-car";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final RestClient restClient;
    private final String apiKey;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OpenRouteServiceAdapter(
            RestClient openRouteServiceRestClient,
            @Value("${napa.routing.ors.api-key:}") String apiKey,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.restClient = Objects.requireNonNull(openRouteServiceRestClient, "openRouteServiceRestClient");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey").trim();
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public RouteResult computeRoute(GeoPoint origin, GeoPoint destination) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        String cacheKey = cacheKey(origin, destination);

        CachedRoute cachedRoute = readCache(cacheKey);
        if (cachedRoute != null) {
            return cachedRoute.toRouteResult();
        }
        if (apiKey.isBlank()) {
            log.error("OpenRouteService API key is not configured (napa.routing.ors.api-key)");
            throw unavailable();
        }

        OrsResponse response;
        try {
            response = restClient.post()
                    .uri(DIRECTIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody(origin, destination))
                    .retrieve()
                    .body(OrsResponse.class);
        } catch (RestClientResponseException exception) {
            // 429 = cuota agotada; 404 = punto sin carretera cercana; 5xx = servicio caído.
            log.warn("OpenRouteService responded with HTTP {}", exception.getStatusCode().value());
            throw unavailable();
        } catch (RestClientException exception) {
            log.warn("OpenRouteService request failed", exception);
            throw unavailable();
        }

        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            log.warn("OpenRouteService returned no route");
            throw unavailable();
        }

        RouteResult result = toRouteResult(response.routes().getFirst());
        writeCache(cacheKey, CachedRoute.from(result));
        return result;
    }

    /** ORS recibe las coordenadas como [longitud, latitud], al revés que Google. */
    private Map<String, Object> requestBody(GeoPoint origin, GeoPoint destination) {
        return Map.of(
                "coordinates", List.of(
                        List.of(origin.longitude(), origin.latitude()),
                        List.of(destination.longitude(), destination.latitude())
                ),
                "instructions", false
        );
    }

    private RouteResult toRouteResult(OrsRoute route) {
        if (route == null || route.summary() == null || route.geometry() == null) {
            log.warn("OpenRouteService returned an incomplete route");
            throw unavailable();
        }
        double distance = route.summary().distance();
        double duration = route.summary().duration();
        if (!Double.isFinite(distance) || !Double.isFinite(duration) || distance < 0 || duration < 0) {
            log.warn("OpenRouteService returned an invalid summary");
            throw unavailable();
        }
        return new RouteResult(
                Math.round(distance),
                Duration.ofSeconds(Math.round(duration)),
                route.geometry()
        );
    }

    private CachedRoute readCache(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, CachedRoute.class);
        } catch (DataAccessException | JacksonException | IllegalArgumentException exception) {
            log.warn("Route cache read failed; requesting a fresh route", exception);
            return null;
        }
    }

    private void writeCache(String key, CachedRoute route) {
        try {
            ValueOperations<String, String> values = redisTemplate.opsForValue();
            values.set(key, objectMapper.writeValueAsString(route), CACHE_TTL);
        } catch (DataAccessException | JacksonException exception) {
            log.warn("Route cache write failed", exception);
        }
    }

    /** Coordenadas a 4 decimales (~11 m): rutas casi idénticas comparten caché. */
    private String cacheKey(GeoPoint origin, GeoPoint destination) {
        String coordinates = coordinate(origin) + ":" + coordinate(destination);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(coordinates.getBytes(StandardCharsets.UTF_8));
            return "route:v2:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime", exception);
        }
    }

    private String coordinate(GeoPoint point) {
        return String.format(Locale.ROOT, "%.4f,%.4f", point.latitude(), point.longitude());
    }

    private DomainException unavailable() {
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible calcular la ruta en este momento."
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrsResponse(List<OrsRoute> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrsRoute(OrsSummary summary, String geometry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrsSummary(double distance, double duration) {}

    public record CachedRoute(long distanceMeters, long durationSeconds, String encodedPolyline) {
        public CachedRoute {
            if (distanceMeters < 0 || durationSeconds < 0) {
                throw new IllegalArgumentException("cached route values must not be negative");
            }
            Objects.requireNonNull(encodedPolyline, "encodedPolyline");
        }

        static CachedRoute from(RouteResult result) {
            return new CachedRoute(
                    result.distanceMeters(),
                    result.duration().toSeconds(),
                    result.encodedPolyline()
            );
        }

        RouteResult toRouteResult() {
            return new RouteResult(
                    distanceMeters,
                    Duration.ofSeconds(durationSeconds),
                    encodedPolyline
            );
        }
    }
}