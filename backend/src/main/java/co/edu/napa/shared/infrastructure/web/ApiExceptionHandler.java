package co.edu.napa.shared.infrastructure.web;

import co.edu.napa.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomainException(
            DomainException exception,
            HttpServletRequest request
    ) {
        return response(
                statusFor(exception.code()),
                exception.code(),
                exception.getMessage(),
                exception.details(),
                request
        );
    }

    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        Map<String, Object> details = exception instanceof BindException bindException
                ? validationDetails(bindException)
                : Map.of();
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "La solicitud contiene datos inválidos.",
                details,
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Debes iniciar sesión para continuar.",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "No tienes permiso para realizar esta acción.",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected API error for request {}", requestId(request), exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ocurrió un error inesperado.",
                Map.of(),
                request
        );
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                code,
                message,
                details,
                requestId(request),
                Instant.now(clock)
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (request.getRequestURI().startsWith("/api/v1/auth/")) {
            builder.header(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        if ("RATE_LIMITED".equals(code)) {
            Object retryAfter = details.get("retryAfter");
            long seconds = retryAfter instanceof Number value ? Math.max(1, value.longValue()) : 1;
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return builder.body(error);
    }

    private Map<String, Object> validationDetails(BindException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getFieldErrors().forEach(error ->
                fields.putIfAbsent(
                        error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "Valor inválido")
                )
        );
        return Map.of("fields", fields);
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        return "unavailable";
    }

    private HttpStatus statusFor(String code) {
        return switch (code) {
            case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            case "UNAUTHORIZED", "TOKEN_EXPIRED", "ACCOUNT_NOT_VERIFIED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "METHOD_NOT_ALLOWED" -> HttpStatus.METHOD_NOT_ALLOWED;
            case "INVALID_STATE", "ALREADY_CONFIRMED", "CONFLICT", "PAYMENT_NOT_CONFIRMED",
                    "ALREADY_PUBLISHED" -> HttpStatus.CONFLICT;
            case "IDEMPOTENCY_KEY_REUSED", "BUSINESS_RULE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "EXTERNAL_SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "INTERNAL_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }
}
