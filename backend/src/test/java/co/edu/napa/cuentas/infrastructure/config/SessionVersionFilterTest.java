package co.edu.napa.cuentas.infrastructure.config;

import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.shared.domain.DomainException;
import co.edu.napa.shared.infrastructure.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionVersionFilterTest {
    private final SessionVersionPort sessionVersions = mock(SessionVersionPort.class);
    private final SessionVersionFilter filter = new SessionVersionFilter(
            sessionVersions,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permitsRequestsWithCurrentSessionVersion() throws Exception {
        UUID sessionId = UUID.randomUUID();
        authenticate(sessionId, 2);
        when(sessionVersions.matches(sessionId, 2)).thenReturn(true);
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> continued.set(true)
        );

        assertTrue(continued.get());
    }

    @Test
    void rejectsRevokedSessions() throws Exception {
        UUID sessionId = UUID.randomUUID();
        authenticate(sessionId, 1);
        when(sessionVersions.matches(sessionId, 1)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertFalse(continued.get());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"UNAUTHORIZED\""));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void returnsServiceUnavailableWhenRedisCannotValidateSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        authenticate(sessionId, 1);
        when(sessionVersions.matches(sessionId, 1)).thenThrow(
                new DomainException(
                        "EXTERNAL_SERVICE_UNAVAILABLE",
                        "Redis no disponible"
                )
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                (request, servletResponse) -> {
                    throw new AssertionError("The request must not continue");
                }
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatus());
        assertEquals("1", response.getHeader("Retry-After"));
    }

    private void authenticate(UUID sessionId, long sessionVersion) {
        Jwt jwt = Jwt.withTokenValue("signed")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("jti", sessionId.toString())
                .claim("sv", sessionVersion)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
