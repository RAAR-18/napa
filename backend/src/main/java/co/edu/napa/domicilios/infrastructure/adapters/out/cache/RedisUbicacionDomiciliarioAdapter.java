package co.edu.napa.domicilios.infrastructure.adapters.out.cache;

import co.edu.napa.domicilios.application.ports.out.UbicacionDomiciliarioPort;
import co.edu.napa.shared.domain.DomainException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Claves {@code loc:{domicilioId}} (TTL 2 min) y {@code loc-limite:{domicilioId}} (un envío cada 5 s). */
@Component
public class RedisUbicacionDomiciliarioAdapter implements UbicacionDomiciliarioPort {
    private static final Duration VIGENCIA = Duration.ofMinutes(2);
    private static final Duration INTERVALO_MINIMO = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;

    public RedisUbicacionDomiciliarioAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean guardar(UUID domicilioId, double latitud, double longitud, Instant ahora) {
        try {
            Boolean permitido = redis.opsForValue().setIfAbsent("loc-limite:" + domicilioId, "1", INTERVALO_MINIMO);
            if (!Boolean.TRUE.equals(permitido)) {
                return false;
            }
            redis.opsForValue().set("loc:" + domicilioId,
                    String.format(Locale.ROOT, "%.6f,%.6f,%d", latitud, longitud, ahora.toEpochMilli()), VIGENCIA);
            return true;
        } catch (DataAccessException e) {
            throw noDisponible();
        }
    }

    @Override
    public Optional<Posicion> consultar(UUID domicilioId) {
        try {
            String valor = redis.opsForValue().get("loc:" + domicilioId);
            if (valor == null) {
                return Optional.empty();
            }
            String[] partes = valor.split(",");
            return Optional.of(new Posicion(Double.parseDouble(partes[0]), Double.parseDouble(partes[1]),
                    Instant.ofEpochMilli(Long.parseLong(partes[2]))));
        } catch (DataAccessException e) {
            // La cronología sigue sirviendo aunque Redis falle: solo falta la posición.
            return Optional.empty();
        }
    }

    @Override
    public void borrar(UUID domicilioId) {
        try {
            redis.delete("loc:" + domicilioId);
        } catch (DataAccessException e) {
            // Vence sola a los 2 minutos.
        }
    }

    private static DomainException noDisponible() {
        return new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", "No pudimos guardar tu ubicación.",
                Map.of("retryAfter", 15));
    }
}
