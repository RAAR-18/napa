package co.edu.napa.shared.infrastructure.idempotency;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import com.github.dockerjava.api.model.ExposedPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
class IdempotencyFilterRedisIntegrationTest {
    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connectToRedis() {
        connectToRedis(redis.getFirstMappedPort());
    }

    private static void connectToRedis(int port) {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(3)).build()
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        if (redisTemplate == null) {
            redisTemplate = new StringRedisTemplate();
        }
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void returnsTheStoredResponseForTheSameUserKeyAndBody() throws Exception {
        IdempotencyFilter filter = filter();
        String idempotencyKey = UUID.randomUUID().toString();
        MockHttpServletRequest originalRequest = request(idempotencyKey, "{\"pedido\":1}");
        MockHttpServletResponse originalResponse = new MockHttpServletResponse();

        filter.doFilter(originalRequest, originalResponse, (request, response) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(201);
            httpResponse.setHeader("X-Request-Id", "trace-original");
            httpResponse.setHeader("Location", "/api/v1/pedidos/123");
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"id\":\"123\"}");
        });

        MockHttpServletRequest replayRequest = request(idempotencyKey, "{\"pedido\":1}");
        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        filter.doFilter(replayRequest, replayResponse, (request, response) -> {
            throw new AssertionError("A replay must not execute the operation again");
        });

        assertEquals(201, replayResponse.getStatus());
        assertEquals("/api/v1/pedidos/123", replayResponse.getHeader("Location"));
        assertNull(replayResponse.getHeader("X-Request-Id"));
        assertEquals("true", replayResponse.getHeader("Idempotent-Replayed"));
        assertEquals("{\"id\":\"123\"}", replayResponse.getContentAsString());
    }

    @Test
    void retriesTheSameWriteAfterRedisRestarts() throws Exception {
        IdempotencyFilter filter = filter();
        String key = UUID.randomUUID().toString();
        String body = "{\"pedido\":1}";
        String containerId = redis.getContainerId();
        var docker = redis.getDockerClient();
        MockHttpServletResponse unavailable = new MockHttpServletResponse();

        docker.stopContainerCmd(containerId).exec();
        try {
            filter.doFilter(request(key, body), unavailable, (req, res) -> {
                throw new AssertionError("The operation must not execute while Redis is unavailable");
            });
            assertEquals(503, unavailable.getStatus());
            assertTrue(unavailable.getContentAsString().contains("EXTERNAL_SERVICE_UNAVAILABLE"));
        } finally {
            closeRedisConnection();
            docker.startContainerCmd(containerId).exec();
            // Docker Desktop may assign a new host port when the container restarts.
            var bindings = docker.inspectContainerCmd(containerId).exec()
                    .getNetworkSettings().getPorts().getBindings();
            int mappedPort = Integer.parseInt(bindings.get(ExposedPort.tcp(6379))[0].getHostPortSpec());
            connectToRedis(mappedPort);
            await().atMost(Duration.ofSeconds(20)).ignoreExceptions().until(() ->
                    "PONG".equals(redisTemplate.execute((RedisCallback<String>) connection -> connection.ping())));
        }

        AtomicInteger executions = new AtomicInteger();
        MockHttpServletResponse recovered = new MockHttpServletResponse();
        filter.doFilter(request(key, body), recovered, (req, res) -> {
            executions.incrementAndGet();
            ((HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"id\":\"123\"}");
        });

        MockHttpServletResponse replay = new MockHttpServletResponse();
        filter.doFilter(request(key, body), replay, (req, res) -> {
            throw new AssertionError("A retry after a lost response must replay without executing");
        });

        assertEquals(201, recovered.getStatus());
        assertEquals(1, executions.get());
        assertEquals(201, replay.getStatus());
        assertEquals(recovered.getContentAsString(), replay.getContentAsString());
        assertEquals("true", replay.getHeader("Idempotent-Replayed"));
    }

    @Test
    void rejectsAChangedBodyForAnExistingKey() throws Exception {
        IdempotencyFilter filter = filter();
        String idempotencyKey = UUID.randomUUID().toString();
        filter.doFilter(
                request(idempotencyKey, "{\"pedido\":1}"),
                new MockHttpServletResponse(),
                (request, response) -> ((HttpServletResponse) response).getWriter().write("{\"ok\":true}")
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
                request(idempotencyKey, "{\"pedido\":2}"),
                response,
                (request, servletResponse) -> {
                    throw new AssertionError("A changed body must not execute the operation again");
                }
        );

        assertEquals(422, response.getStatus());
        assertTrue(response.getContentAsString().contains("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void concurrentDuplicateDoesNotExecuteWhileTheFirstRequestIsInProgress() throws Exception {
        IdempotencyFilter filter = filter();
        String idempotencyKey = UUID.randomUUID().toString();
        CountDownLatch operationStarted = new CountDownLatch(1);
        CountDownLatch finishOperation = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        MockHttpServletResponse originalResponse = new MockHttpServletResponse();
        var originalOperation = executor.submit(() -> {
            filter.doFilter(
                    request(idempotencyKey, "{\"pedido\":1}"),
                    originalResponse,
                    (request, response) -> {
                        operationStarted.countDown();
                        try {
                            if (!finishOperation.await(5, TimeUnit.SECONDS)) {
                                throw new ServletException("Timed out waiting to finish test operation");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new ServletException(exception);
                        }
                        ((HttpServletResponse) response).setStatus(201);
                    }
            );
            return null;
        });

        try {
            assertTrue(operationStarted.await(5, TimeUnit.SECONDS));
            MockHttpServletResponse duplicateResponse = new MockHttpServletResponse();
            filter.doFilter(
                    request(idempotencyKey, "{\"pedido\":1}"),
                    duplicateResponse,
                    (request, response) -> {
                        throw new AssertionError("A concurrent duplicate must not execute");
                    }
            );

            assertEquals(409, duplicateResponse.getStatus());
            assertTrue(duplicateResponse.getContentAsString().contains("IDEMPOTENCY_IN_PROGRESS"));
            assertEquals("1", duplicateResponse.getHeader("Retry-After"));
            finishOperation.countDown();
            originalOperation.get(5, TimeUnit.SECONDS);
            MockHttpServletResponse replay = new MockHttpServletResponse();
            filter.doFilter(request(idempotencyKey, "{\"pedido\":1}"), replay,
                    (request, response) -> {
                        throw new AssertionError("A completed duplicate must replay without executing");
                    });
            assertEquals(201, replay.getStatus());
            assertEquals("true", replay.getHeader("Idempotent-Replayed"));
        } finally {
            finishOperation.countDown();
            executor.shutdownNow();
        }
    }

    private IdempotencyFilter filter() {
        return new IdempotencyFilter(redisTemplate, JsonMapper.builder().build(), Clock.systemUTC());
    }

    private MockHttpServletRequest request(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/pedidos");
        request.setUserPrincipal(() -> "user-123");
        request.addHeader("Idempotency-Key", key);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
