package co.edu.napa.cuentas.infrastructure.adapters.out.cache;

import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class RedisLimitadorVerificacionAdapterRedisIntegrationTest {
    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisLimitadorVerificacionAdapter adapter;

    @BeforeAll
    static void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getFirstMappedPort())
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        adapter = new RedisLimitadorVerificacionAdapter(redisTemplate);
    }

    @AfterAll
    static void closeRedisConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void aFailedSendReturnedToTheLimiterDoesNotCount() {
        String celular = "+573001234567";
        String ip = "192.0.2.10";
        for (int i = 0; i < 6; i++) {
            assertDoesNotThrow(() -> adapter.registrarEnvio(celular, ip));
            adapter.liberarEnvio(celular, ip);
        }

        assertDoesNotThrow(() -> adapter.registrarEnvio(celular, ip));
    }

    @Test
    void blocksTheSeventhHourlySendToTheSamePhone() {
        String celular = "+573001234568";
        String ip = "192.0.2.11";

        for (int i = 0; i < 6; i++) {
            assertDoesNotThrow(() -> adapter.registrarEnvio(celular, ip));
        }

        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.registrarEnvio(celular, ip)
        );

        assertEquals("RATE_LIMITED", exception.code());
    }

    @Test
    void blocksTheTwentyFirstHourlySendFromTheSameIp() {
        String ip = "192.0.2.12";

        for (int i = 1; i <= 20; i++) {
            String celular = String.format(Locale.ROOT, "+57300%07d", i);
            assertDoesNotThrow(() -> adapter.registrarEnvio(celular, ip));
        }

        String celularNumeroVeintiuno = "+573000000021";
        DomainException exception = assertThrows(
                DomainException.class,
                () -> adapter.registrarEnvio(celularNumeroVeintiuno, ip)
        );

        assertEquals("RATE_LIMITED", exception.code());
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}