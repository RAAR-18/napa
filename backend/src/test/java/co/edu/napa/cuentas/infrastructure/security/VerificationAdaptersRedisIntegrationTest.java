package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class VerificationAdaptersRedisIntegrationTest {
    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);
    private static final RedisScript<Long> REDIS_TIME_MILLIS = new DefaultRedisScript<>(
            """
            local time = redis.call('TIME')
            return tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            """,
            Long.class
    );

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getFirstMappedPort())
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisConnection() {
        connectionFactory.destroy();
    }

    @Test
    void enforcesTheOneRequestPerMinuteLimitAcrossTheRealRedisScript() {
        RedisVerificationRateLimitAdapter adapter = new RedisVerificationRateLimitAdapter(redisTemplate);
        String celular = "+57300" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String ipAddress = "ip-" + UUID.randomUUID();
        adapter.checkAndRecord(celular, ipAddress);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.checkAndRecord(celular, ipAddress)
        );

        assertEquals("RATE_LIMITED", exception.code());
        assertTrue(((Number) exception.details().get("retryAfter")).longValue() <= 60);
    }

    @Test
    void enforcesTheFiveRequestsPerHourLimitAcrossTheRealRedisScript() throws Exception {
        RedisVerificationRateLimitAdapter adapter = new RedisVerificationRateLimitAdapter(redisTemplate);
        String celular = "+57300" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String key = "auth:verification:phone:" + digest(celular);
        long score = redisTemplate.execute(REDIS_TIME_MILLIS, List.of()) - 61_000;
        for (int attempt = 0; attempt < 5; attempt++) {
            redisTemplate.opsForZSet().add(key, "prior-" + attempt, score);
        }

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.checkAndRecord(celular, "ip-" + UUID.randomUUID())
        );

        assertEquals("RATE_LIMITED", exception.code());
        long retryAfter = ((Number) exception.details().get("retryAfter")).longValue();
        assertTrue(retryAfter > 3_500 && retryAfter <= 3_600);
    }

    @Test
    void consoleVerificationExpiresAfterFiveFailuresAndApprovesOnlyOnce() {
        ConsoleVerificarCelularAdapter adapter =
                new ConsoleVerificarCelularAdapter(redisTemplate, "123456");
        String celular = "+57300" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        adapter.solicitar(celular, EstadoVerificacion.REGISTRO, "ignored");

        for (int remaining = 4; remaining > 0; remaining--) {
            VerificacionResultado result = adapter.confirmar(
                    celular,
                    EstadoVerificacion.REGISTRO,
                    "000000"
            );
            assertEquals(VerificacionResultado.Estado.INCORRECTA, result.estado());
            assertEquals(remaining, result.intentosRestantes());
        }
        assertEquals(
                VerificacionResultado.Estado.AGOTADA,
                adapter.confirmar(celular, EstadoVerificacion.REGISTRO, "000000").estado()
        );
        assertEquals(
                VerificacionResultado.Estado.VENCIDA,
                adapter.confirmar(celular, EstadoVerificacion.REGISTRO, "123456").estado()
        );

        adapter.solicitar(celular, EstadoVerificacion.REGISTRO, "ignored");
        assertEquals(
                VerificacionResultado.Estado.APROBADA,
                adapter.confirmar(celular, EstadoVerificacion.REGISTRO, "123456").estado()
        );
        assertEquals(
                VerificacionResultado.Estado.VENCIDA,
                adapter.confirmar(celular, EstadoVerificacion.REGISTRO, "123456").estado()
        );
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
