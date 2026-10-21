package co.edu.napa.shared.infrastructure.web;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private final ApiExceptionHandler handler = new ApiExceptionHandler(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void mapsInvalidStateToConflictAndIncludesRequestTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "trace-123");

        var response = handler.handleDomainException(
                new DomainException(
                        "INVALID_STATE",
                        "El estado actual no permite esta acción.",
                        Map.of("estadoActual", "ENTREGADO")
                ),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INVALID_STATE", response.getBody().code());
        assertEquals("trace-123", response.getBody().traceId());
        assertEquals("ENTREGADO", response.getBody().details().get("estadoActual"));
        assertEquals(NOW, response.getBody().timestamp());
    }

    @Test
    void rateLimitErrorIncludesRetryAfterHeader() {
        var response = handler.handleDomainException(
                new DomainException(
                        "RATE_LIMITED",
                        "Inténtalo más tarde.",
                        Map.of("retryAfter", 120)
                ),
                new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("120", response.getHeaders().getFirst("Retry-After"));
    }
}
