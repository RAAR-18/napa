package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtTokenServiceTest {
    @Test
    void issuesSignedAccessTokenWithRoleAndSessionVersionClaims() {
        byte[] secret = "test-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
        var secretKey = new SecretKeySpec(secret, "HmacSHA256");
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableSecret<>(secretKey)
        );
        SessionVersionPort sessionVersions = mock(SessionVersionPort.class);
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        JwtTokenService service = new JwtTokenService(
                encoder,
                sessionVersions,
                clock,
                Duration.ofMinutes(15),
                Duration.ofDays(30)
        );
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Usuario user = new Usuario(
                userId,
                "Ana",
                "+573001234567",
                null,
                "encoded-password",
                RolUsuario.CLIENTE,
                EstadoUsuario.ACTIVA,
                clock.instant(),
                clock.instant(),
                clock.instant()
        );
        Sesion session = new Sesion(
                sessionId,
                userId,
                "refresh-hash",
                "device-1",
                3,
                clock.instant(),
                clock.instant().plus(Duration.ofDays(30)),
                null
        );

        IssuedTokens issued = service.issue(session, user, "opaque-refresh");
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        Jwt decoded = decoder.decode(issued.accessToken());

        assertEquals(userId.toString(), decoded.getSubject());
        assertEquals(sessionId.toString(), decoded.getId());
        assertEquals("CLIENTE", decoded.getClaimAsString("rol"));
        assertEquals(3L, ((Number) decoded.getClaim("sv")).longValue());
        assertEquals(900, issued.expiresIn());
        assertEquals("opaque-refresh", issued.refreshToken());
        assertNotEquals("refresh-hash", issued.refreshToken());
        verify(sessionVersions).set(sessionId, 3, Duration.ofDays(30));
    }
}
