package co.edu.napa.shared.domain;

import java.util.Map;
import java.util.Objects;

public final class DomainException extends RuntimeException {
    private final String code;
    private final Map<String, Object> details;

    public DomainException(String code, String message) {
        this(code, message, Map.of());
    }

    public DomainException(String code, String message, Map<String, Object> details) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
        this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
