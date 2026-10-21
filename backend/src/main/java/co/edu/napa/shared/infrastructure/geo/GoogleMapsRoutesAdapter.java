package co.edu.napa.shared.infrastructure.geo;

import co.edu.napa.shared.application.ports.out.RoutingPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.domain.geo.GeoPoint;
import co.edu.napa.shared.domain.geo.RouteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class GoogleMapsRoutesAdapter implements RoutingPort {
    private static final Logger log = LoggerFactory.getLogger(GoogleMapsRoutesAdapter.class);
    private static final String FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Pattern GOOGLE_DURATION = Pattern.compile("\\d+(\\.\\d+)?s");

    private final RestClient restClient;
    private final String apiKey;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GoogleMapsRoutesAdapter(
            RestClient googleMapsRoutesRestClient,
            @Value("${napa.maps.api-key:}") String apiKey,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.restClient = Objects.requireNonNull(googleMapsRoutesRestClient, "googleMapsRoutesRestClient");
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
            log.error("Google Maps Routes API key is not configured");
            throw unavailable();
        }

        GoogleRoutesResponse response;
        try {
            response = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(requestBody(origin, destination))
                    .retrieve()
                    .body(GoogleRoutesResponse.class);
        } catch (RestClientException exception) {
            log.warn("Google Maps Routes API request failed", exception);
            throw unavailable();
        }

        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            log.warn("Google Maps Routes API returned no route");
            throw unavailable();
        }

        RouteResult result = toRouteResult(response.routes().getFirst());
        writeCache(cacheKey, CachedRoute.from(result));
        return result;
    }

    private Map<String, Object> requestBody(GeoPoint origin, GeoPoint destination) {
        return Map.of(
                "origin", waypoint(origin),
                "destination", waypoint(destination),
                "travelMode", "DRIVE",
                "languageCode", "es-CO",
                "units", "METRIC",
                "computeAlternativeRoutes", false
        );
    }

    private Map<String, Object> waypoint(GeoPoint point) {
        return Map.of(
                "location",
                Map.of("latLng", Map.of(
                        "latitude", point.latitude(),
                        "longitude", point.longitude()
                ))
        );
    }

    private RouteResult toRouteResult(GoogleRoute route) {
        if (route == null || route.duration() == null || route.polyline() == null
                || route.polyline().encodedPolyline() == null) {
            log.warn("Google Maps Routes API returned an incomplete route");
            throw unavailable();
        }

        if (!GOOGLE_DURATION.matcher(route.duration()).matches()) {
            log.warn("Google Maps Routes API returned an invalid duration");
            throw unavailable();
        }

        try {
            return new RouteResult(
                    route.distanceMeters(),
                    Duration.parse("PT" + route.duration().toUpperCase()),
                    route.polyline().encodedPolyline()
            );
        } catch (RuntimeException exception) {
            log.warn("Google Maps Routes API response could not be parsed", exception);
            throw unavailable();
        }
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

    private String cacheKey(GeoPoint origin, GeoPoint destination) {
        String coordinates = origin.latitude() + "," + origin.longitude()
                + ":" + destination.latitude() + "," + destination.longitude();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(coordinates.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "route:v1:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime", exception);
        }
    }

    private DomainException unavailable() {
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible calcular la ruta en este momento."
        );
    }

    public record GoogleRoutesResponse(List<GoogleRoute> routes) {}

    public record GoogleRoute(long distanceMeters, String duration, GooglePolyline polyline) {}

    public record GooglePolyline(String encodedPolyline) {}

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
