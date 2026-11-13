package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.domain.model.DispositivoToken;
import co.edu.napa.cuentas.domain.model.PlataformaDispositivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispositivo_token")
public class DispositivoTokenJpaEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioJpaEntity usuario;

    @Column(nullable = false, columnDefinition = "text")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlataformaDispositivo plataforma;

    @Column(name = "ultima_conexion_at", nullable = false)
    private Instant ultimaConexionAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DispositivoTokenJpaEntity() {
    }

    public DispositivoTokenJpaEntity(DispositivoToken dispositivoToken, UsuarioJpaEntity usuario) {
        id = dispositivoToken.id();
        this.usuario = usuario;
        token = dispositivoToken.token();
        plataforma = dispositivoToken.plataforma();
        ultimaConexionAt = dispositivoToken.ultimaConexionAt();
        createdAt = dispositivoToken.createdAt();
        updatedAt = dispositivoToken.updatedAt();
    }

    public DispositivoToken toDomain() {
        return new DispositivoToken(
                id, usuario.getId(), token, plataforma, ultimaConexionAt, createdAt, updatedAt
        );
    }
}
