package co.edu.napa.cuentas.infrastructure.adapters.out.persistence;

import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class UsuarioJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 16, unique = true)
    private String celular;

    @Column(length = 254)
    private String correo;

    @Column(name = "foto_url", columnDefinition = "text")
    private String fotoUrl;

    @Column(name = "ubicacion_id")
    private UUID ubicacionId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RolUsuario rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EstadoUsuario estado;

    @Column(name = "celular_verificado_at")
    private Instant celularVerificadoAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UsuarioJpaEntity() {
    }

    public UsuarioJpaEntity(Usuario usuario) {
        id = usuario.id();
        nombre = usuario.nombre();
        celular = usuario.celular();
        correo = usuario.correo();
        fotoUrl = usuario.fotoUrl();
        ubicacionId = usuario.ubicacionId();
        passwordHash = usuario.passwordHash();
        rol = usuario.rol();
        estado = usuario.estado();
        celularVerificadoAt = usuario.celularVerificadoAt();
        createdAt = usuario.createdAt();
        updatedAt = usuario.updatedAt();
    }

    public Usuario toDomain() {
        return new Usuario(
                id, nombre, celular, correo, fotoUrl, ubicacionId, passwordHash, rol, estado,
                celularVerificadoAt, createdAt, updatedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
