package co.edu.napa.cuentas.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Usuario(
        UUID id,
        String nombre,
        String celular,
        String correo,
        String fotoUrl,
        UUID ubicacionId,
        String passwordHash,
        RolUsuario rol,
        EstadoUsuario estado,
        Instant celularVerificadoAt,
        Instant createdAt,
        Instant updatedAt
) {
    public Usuario {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(celular, "celular");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(rol, "rol");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Usuario(
            UUID id,
            String nombre,
            String celular,
            String correo,
            String passwordHash,
            RolUsuario rol,
            EstadoUsuario estado,
            Instant celularVerificadoAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id, nombre, celular, correo, null, null, passwordHash, rol, estado,
                celularVerificadoAt, createdAt, updatedAt
        );
    }

    public Usuario activar(Instant verifiedAt) {
        if (estado != EstadoUsuario.PENDIENTE_VERIFICACION) {
            throw new IllegalStateException("Only pending users can be activated");
        }
        return new Usuario(
                id, nombre, celular, correo, fotoUrl, ubicacionId, passwordHash, rol,
                EstadoUsuario.ACTIVA, Objects.requireNonNull(verifiedAt, "verifiedAt"),
                createdAt, verifiedAt
        );
    }

    public Usuario cambiarContrasena(String newPasswordHash, Instant changedAt) {
        if (estado != EstadoUsuario.ACTIVA && rol != RolUsuario.ADMINISTRADOR) {
            throw new IllegalStateException("Only active users can change their password");
        }
        return new Usuario(
                id, nombre, celular, correo, fotoUrl, ubicacionId,
                Objects.requireNonNull(newPasswordHash, "newPasswordHash"),
                rol, estado, celularVerificadoAt, createdAt, Objects.requireNonNull(changedAt, "changedAt")
        );
    }
}
