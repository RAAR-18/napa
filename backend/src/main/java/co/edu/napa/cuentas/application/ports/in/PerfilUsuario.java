package co.edu.napa.cuentas.application.ports.in;

import java.time.Instant;
import java.util.UUID;

/** Cuerpo de {@code GET /usuarios/me} (RF-08) y de {@code GET /usuarios/{id}} (RF-66); sin contraseña ni tokens. */
public record PerfilUsuario(
        UUID id,
        String nombre,
        String celular,
        String correo,
        String fotoUrl,
        String rol,
        String estado,
        boolean celularVerificado,
        Instant createdAt,
        Ubicacion ubicacion
) {
    public record Ubicacion(double latitud, double longitud, String direccion, String referencia) {}
}
