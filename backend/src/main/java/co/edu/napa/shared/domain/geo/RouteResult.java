package co.edu.napa.shared.domain.geo;

import java.time.Duration;
import java.util.Objects;

public record RouteResult(long distanceMeters, Duration duration, String encodedPolyline) {
    public RouteResult {
        if (distanceMeters < 0) {
            throw new IllegalArgumentException("distanceMeters must not be negative");
        }
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(encodedPolyline, "encodedPolyline");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }
}
