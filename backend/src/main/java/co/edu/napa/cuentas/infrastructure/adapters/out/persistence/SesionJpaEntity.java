package co.edu.napa.cuentas.infrastructure.persistence;

import co.edu.napa.cuentas.domain.model.Sesion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sesion")
public class SesionJpaEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioJpaEntity usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "session_version", nullable = false)
    private long sessionVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SesionJpaEntity() {
    }

    public SesionJpaEntity(Sesion sesion, UsuarioJpaEntity usuario) {
        id = sesion.id();
        this.usuario = usuario;
        tokenHash = sesion.refreshTokenHash();
        deviceId = sesion.deviceId();
        sessionVersion = sesion.sessionVersion();
        createdAt = sesion.createdAt();
        expiresAt = sesion.expiresAt();
        revokedAt = sesion.revokedAt();
    }

    public Sesion toDomain() {
        return new Sesion(
                id, usuario.getId(), tokenHash, deviceId, sessionVersion,
                createdAt, expiresAt, revokedAt
        );
    }

    public void rotate(String nextTokenHash) {
        tokenHash = nextTokenHash;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
            sessionVersion++;
        }
    }

    public UUID getId() {
        return id;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }
}
