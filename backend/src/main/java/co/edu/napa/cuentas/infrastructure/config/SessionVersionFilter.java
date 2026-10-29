package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.infrastructure.web.ApiError;
import co.edu.napa.shared.infrastructure.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SessionVersionFilter extends OncePerRequestFilter {
    private final SessionVersionPort sessionVersions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SessionVersionFilter(
            SessionVersionPort sessionVersions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.sessionVersions = sessionVersions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID sessionId;
        Long sessionVersion = jwtAuthentication.getToken().getClaim("sv");
        try {
            sessionId = UUID.fromString(jwtAuthentication.getToken().getId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            reject(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "La sesión no es válida.");
            return;
        }
        if (sessionVersion == null) {
            reject(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "La sesión no es válida.");
            return;
        }

        try {
            if (!sessionVersions.matches(sessionId, sessionVersion)) {
                reject(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "La sesión fue revocada.");
                return;
            }
        } catch (DomainException exception) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            reject(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception.code(),
                    exception.getMessage()
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        SecurityContextHolder.clearContext();
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        String traceId = requestId instanceof String value && !value.isBlank()
                ? value
                : "unavailable";
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiError(code, message, Map.of(), traceId, Instant.now(clock))
        );
    }
}
