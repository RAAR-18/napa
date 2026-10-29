package co.edu.napa.shared.infrastructure.idempotency;

import co.edu.napa.shared.infrastructure.web.ApiError;
import co.edu.napa.shared.infrastructure.web.RequestIdFilter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String DEVICE_HEADER = "X-Device-Id";
    private static final String KEY_PREFIX = "idem:";
    private static final String IN_PROGRESS = "IN_PROGRESS:";
    private static final String DONE = "DONE:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "content-length", "transfer-encoding", "connection", "keep-alive", "set-cookie", "x-request-id"
    );
    private static final RedisScript<Long> COMPLETE_LOCK = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """,
            Long.class
    );
    private static final RedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean exempt = path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/logout")
                || path.startsWith("/api/v1/auth/verify-phone")
                || path.equals("/api/v1/auth/password/recover/request");
        return exempt || !(path.startsWith("/api/v1/")
                && ("POST".equalsIgnoreCase(request.getMethod())
                || "PATCH".equalsIgnoreCase(request.getMethod())));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String keyHeader = request.getHeader(IDEMPOTENCY_HEADER);
        if (keyHeader == null || keyHeader.isBlank()) {
            writeError(
                    request,
                    response,
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "La cabecera Idempotency-Key es obligatoria.",
                    Map.of()
            );
            return;
        }

        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(keyHeader);
        } catch (IllegalArgumentException exception) {
            writeError(
                    request,
                    response,
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "La cabecera Idempotency-Key debe ser un UUID.",
                    Map.of()
            );
            return;
        }

        String scope = request.getUserPrincipal() == null
                ? request.getHeader(DEVICE_HEADER)
                : request.getUserPrincipal().getName();
        if (scope == null || scope.isBlank()) {
            writeError(
                    request,
                    response,
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Se requiere una identidad autenticada o la cabecera X-Device-Id.",
                    Map.of()
            );
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String requestHash = requestHash(request, body);
        String redisKey = KEY_PREFIX + sha256(scope) + ":" + idempotencyKey;
        String lockValue = IN_PROGRESS + requestHash + ":" + UUID.randomUUID();

        try {
            ValueOperations<String, String> values = redisTemplate.opsForValue();
            for (int attempt = 0; attempt < 2; attempt++) {
                Boolean acquired = values.setIfAbsent(redisKey, lockValue, LOCK_TTL);
                if (Boolean.TRUE.equals(acquired)) {
                    executeAndCache(
                            request,
                            response,
                            filterChain,
                            body,
                            redisKey,
                            lockValue,
                            requestHash
                    );
                    return;
                }

                String existing = values.get(redisKey);
                if (existing == null) {
                    continue;
                }
                handleExisting(request, response, existing, requestHash);
                return;
            }

            writeError(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EXTERNAL_SERVICE_UNAVAILABLE",
                    "No fue posible confirmar la idempotencia de la solicitud.",
                    Map.of()
            );
            response.setHeader("Retry-After", "1");
        } catch (DataAccessException exception) {
            log.error("Redis unavailable while checking idempotency key {}", idempotencyKey, exception);
            writeError(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EXTERNAL_SERVICE_UNAVAILABLE",
                    "No fue posible confirmar la idempotencia de la solicitud.",
                    Map.of()
            );
            response.setHeader("Retry-After", "1");
        }
    }

    private void handleExisting(
            HttpServletRequest request,
            HttpServletResponse response,
            String existing,
            String requestHash
    ) throws IOException {
        if (existing.startsWith(IN_PROGRESS)) {
            String[] parts = existing.split(":", 3);
            if (parts.length != 3) {
                log.error("Malformed in-progress idempotency record");
                writeServiceUnavailable(request, response);
                return;
            }
            if (!parts[1].equals(requestHash)) {
                writeKeyReused(request, response);
                return;
            }
            writeError(
                    request,
                    response,
                    HttpStatus.CONFLICT,
                    "CONFLICT",
                    "La solicitud con esta clave todavía se está procesando.",
                    Map.of()
            );
            response.setHeader("Retry-After", "1");
            return;
        }

        if (!existing.startsWith(DONE)) {
            log.error("Unknown idempotency record state");
            writeServiceUnavailable(request, response);
            return;
        }

        CachedResponse cached;
        try {
            cached = objectMapper.readValue(existing.substring(DONE.length()), CachedResponse.class);
        } catch (JacksonException | IllegalArgumentException exception) {
            log.error("Could not decode cached idempotent response", exception);
            writeServiceUnavailable(request, response);
            return;
        }
        if (!cached.requestHash().equals(requestHash)) {
            writeKeyReused(request, response);
            return;
        }

        try {
            replay(cached, response);
        } catch (IllegalArgumentException exception) {
            log.error("Cached idempotent response is malformed", exception);
            writeServiceUnavailable(request, response);
        }
    }

    private void executeAndCache(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            byte[] body,
            String redisKey,
            String lockValue,
            String requestHash
    ) throws IOException, ServletException {
        CachedBodyRequest wrappedRequest = new CachedBodyRequest(request, body);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (IOException | ServletException | RuntimeException exception) {
            releaseLock(redisKey, lockValue);
            throw exception;
        }

        CachedResponse cached = new CachedResponse(
                requestHash,
                wrappedResponse.getStatus(),
                captureHeaders(wrappedResponse),
                Base64.getEncoder().encodeToString(wrappedResponse.getContentAsByteArray())
        );
        try {
            String serialized = DONE + objectMapper.writeValueAsString(cached);
            Long stored = redisTemplate.execute(
                    COMPLETE_LOCK,
                    List.of(redisKey),
                    lockValue,
                    serialized,
                    Long.toString(RESULT_TTL.toSeconds())
            );
            if (!Long.valueOf(1).equals(stored)) {
                log.error("Idempotency lock was lost before the response could be cached");
            }
        } catch (DataAccessException | JacksonException exception) {
            log.error("Could not cache completed idempotent response", exception);
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    private Map<String, List<String>> captureHeaders(ContentCachingResponseWrapper response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        response.getHeaderNames().forEach(name -> {
            if (!EXCLUDED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, List.copyOf(response.getHeaders(name)));
            }
        });
        return Map.copyOf(headers);
    }

    private void replay(CachedResponse cached, HttpServletResponse response) throws IOException {
        byte[] body = Base64.getDecoder().decode(cached.body());
        response.setStatus(cached.status());
        cached.headers().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setHeader("Idempotent-Replayed", "true");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private void writeKeyReused(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(
                request,
                response,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "IDEMPOTENCY_KEY_REUSED",
                "La clave Idempotency-Key ya se usó con una solicitud diferente.",
                Map.of()
        );
    }

    private void writeServiceUnavailable(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        writeError(
                request,
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible confirmar la idempotencia de la solicitud.",
                Map.of()
        );
        response.setHeader("Retry-After", "1");
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details
    ) throws IOException {
        String traceId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        ApiError error = new ApiError(code, message, details, traceId, Instant.now(clock));
        response.setStatus(status.value());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader(RequestIdFilter.HEADER_NAME, traceId);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private void releaseLock(String redisKey, String lockValue) {
        try {
            redisTemplate.execute(RELEASE_LOCK, List.of(redisKey), lockValue);
        } catch (DataAccessException exception) {
            log.error("Could not release failed idempotency lock", exception);
        }
    }

    private String requestHash(HttpServletRequest request, byte[] body) {
        MessageDigest digest = newSha256();
        digest.update(request.getMethod().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Objects.toString(request.getQueryString(), "").getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(body);
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(String value) {
        return HexFormat.of().formatHex(newSha256().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime", exception);
        }
    }

    private record CachedResponse(
            String requestHash,
            int status,
            Map<String, List<String>> headers,
            String body
    ) {
        private CachedResponse {
            if (requestHash == null || requestHash.isBlank()
                    || status < 100 || status > 599
                    || headers == null
                    || body == null) {
                throw new IllegalArgumentException("Invalid cached idempotent response");
            }
            headers = Map.copyOf(headers);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private ServletInputStream servletInputStream;
        private BufferedReader reader;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            if (servletInputStream == null) {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                servletInputStream = new ServletInputStream() {
                    @Override
                    public int read() {
                        return input.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return input.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        throw new UnsupportedOperationException("Asynchronous request reading is not supported");
                    }
                };
            }
            return servletInputStream;
        }

        @Override
        public BufferedReader getReader() {
            if (reader == null) {
                reader = new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
            return reader;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
