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
        details = details == null ? Map.of() : Map.copyOf(details);
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public ApiError(String code, String message, Map<String, Object> details, String traceId) {
        this(code, message, details, traceId, Instant.now());
    }

    public ApiError(String code, String message, String traceId) {
        this(code, message, Map.of(), traceId, Instant.now());
    }
}
