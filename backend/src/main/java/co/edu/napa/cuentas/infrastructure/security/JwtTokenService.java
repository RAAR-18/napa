package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.SessionVersionPort;
import co.edu.napa.cuentas.application.ports.out.IssuedTokens;
import co.edu.napa.cuentas.application.ports.out.TokenServicePort;
import co.edu.napa.cuentas.domain.model.Sesion;
import co.edu.napa.cuentas.domain.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Component
public class JwtTokenService implements TokenServicePort {
    private final JwtEncoder jwtEncoder;
    private final SessionVersionPort sessionVersionPort;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            SessionVersionPort sessionVersionPort,
            Clock clock,
            @Value("${napa.auth.access-token-ttl:PT15M}") Duration accessTokenTtl,
            @Value("${napa.auth.refresh-token-ttl:P30D}") Duration refreshTokenTtl
    ) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder");
        this.sessionVersionPort = Objects.requireNonNull(sessionVersionPort, "sessionVersionPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.accessTokenTtl = Objects.requireNonNull(accessTokenTtl, "accessTokenTtl");
        this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl");
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("accessTokenTtl must be positive");
        }
        if (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("refreshTokenTtl must be positive");
        }
    }

    @Override
    public IssuedTokens issue(Sesion session, Usuario user, String rawRefreshToken) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("napa-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.id().toString())
                .id(session.id().toString())
                .claim("rol", user.rol().name())
                .claim("sv", session.sessionVersion())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
        sessionVersionPort.set(session.id(), session.sessionVersion(), Duration.between(now, session.expiresAt()));
        return new IssuedTokens(
                accessToken,
                rawRefreshToken,
                accessTokenTtl.toSeconds()
        );
    }

    @Override
    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashRefreshToken(String refreshToken) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(refreshToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }
}
