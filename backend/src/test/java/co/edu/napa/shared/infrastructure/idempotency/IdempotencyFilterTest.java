package co.edu.napa.shared.infrastructure.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {
    @Test
    void rejectsWriteWithoutIdempotencyKey() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        IdempotencyFilter filter = filter(redisTemplate);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("The request must not reach the application");
        });

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"VALIDATION_ERROR\""));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void doesNotApplyIdempotencyRequirementToAuthenticationFlowsWithoutContractedKey() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        IdempotencyFilter filter = filter(redisTemplate);

        for (String path : new String[]{
                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                "/api/v1/auth/verify-phone/request", "/api/v1/auth/verify-phone/confirm",
                "/api/v1/auth/password/recover/request"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(202));

            assertEquals(202, response.getStatus(), path);
        }

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsReusingKeyWithDifferentBody() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(2))))
                .thenReturn(false);
        when(values.get(anyString())).thenReturn(
                "DONE:{\"requestHash\":\"different-request\",\"status\":201,\"headers\":{},\"body\":\"b2s=\"}"
        );

        MockHttpServletRequest request = request();
        request.addHeader("Idempotency-Key", UUID.randomUUID().toString());
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(redisTemplate).doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("A cached key mismatch must not reach the application");
        });

        assertEquals(422, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\""));
        verify(values).get(anyString());
    }

    private IdempotencyFilter filter(StringRedisTemplate redisTemplate) {
        return new IdempotencyFilter(
                redisTemplate,
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/pedidos");
        request.setUserPrincipal(() -> "user-123");
        request.setContentType("application/json");
        return request;
    }
}
