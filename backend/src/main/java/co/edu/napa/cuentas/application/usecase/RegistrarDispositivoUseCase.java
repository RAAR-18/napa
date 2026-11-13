package co.edu.napa.cuentas.application.usecase;

import co.edu.napa.cuentas.application.ports.in.RegistrarDispositivoPort;
import co.edu.napa.cuentas.application.ports.out.DispositivoTokenRepositoryPort;
import co.edu.napa.cuentas.domain.model.DispositivoToken;
import co.edu.napa.cuentas.domain.model.PlataformaDispositivo;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class RegistrarDispositivoUseCase implements RegistrarDispositivoPort {
    /** Los tokens de FCM rondan los 160 caracteres; se deja margen. */
    static final int TOKEN_MAXIMO = 4096;

    private final DispositivoTokenRepositoryPort dispositivos;
    private final Clock clock;

    public RegistrarDispositivoUseCase(DispositivoTokenRepositoryPort dispositivos, Clock clock) {
        this.dispositivos = Objects.requireNonNull(dispositivos, "dispositivos");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void registrar(UUID usuarioId, String token, String plataforma) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        String limpio = tokenValido(token);
        PlataformaDispositivo tipo = plataforma(plataforma);
        Instant now = Instant.now(clock);
        // Otra cuenta que usó antes este mismo teléfono deja de recibir sus avisos aquí.
        dispositivos.deleteByTokenForOtherUsers(limpio, usuarioId);
        DispositivoToken dispositivo = dispositivos.findByUsuarioIdAndToken(usuarioId, limpio)
                .map(actual -> new DispositivoToken(actual.id(), usuarioId, limpio, tipo, now, actual.createdAt(), now))
                .orElseGet(() -> new DispositivoToken(UUID.randomUUID(), usuarioId, limpio, tipo, now, now, now));
        dispositivos.save(dispositivo);
    }

    @Override
    @Transactional
    public void darDeBaja(UUID usuarioId, String token) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        dispositivos.deleteByUsuarioIdAndToken(usuarioId, tokenValido(token));
    }

    private static String tokenValido(String token) {
        String limpio = token == null ? "" : token.strip();
        if (limpio.isEmpty() || limpio.length() > TOKEN_MAXIMO) {
            throw invalid("token", "El token del dispositivo no es válido.");
        }
        return limpio;
    }

    private static PlataformaDispositivo plataforma(String value) {
        if (value == null) {
            throw invalid("plataforma", "Indica la plataforma: ANDROID o IOS.");
        }
        try {
            return PlataformaDispositivo.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("plataforma", "Indica la plataforma: ANDROID o IOS.");
        }
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException("VALIDATION_ERROR", message, Map.of("fields", Map.of(field, message)));
    }
}
