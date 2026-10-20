package co.edu.napa.shared.infrastructure.web;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        String traceId,
        Instant timestamp
) {
    public ApiError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
