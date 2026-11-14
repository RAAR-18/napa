package co.edu.napa.domicilios.infrastructure.adapters.out.cache;

import co.edu.napa.domicilios.application.ports.out.IntentosCodigoEntregaPort;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Contador {@code code-att:{domicilioId}} y bloqueo {@code code-lock:{domicilioId}} (RF-24 FR-006). */
@Component
public class RedisIntentosCodigoEntregaAdapter implements IntentosCodigoEntregaPort {
    private static final Logger log = LoggerFactory.getLogger(RedisIntentosCodigoEntregaAdapter.class);
    /** Los fallos se olvidan si pasa un día sin intentos; un domicilio no dura tanto en camino. */
    private static final Duration VENTANA_FALLOS = Duration.ofDays(1);
    private static final RedisScript<Long> REGISTRAR_FALLO = new DefaultRedisScript<>(
            """
            local count = redis.call('incr', KEYS[1])
            if count == 1 then redis.call('expire', KEYS[1], tonumber(ARGV[1])) end
            local remaining = tonumber(ARGV[2]) - count
            if remaining <= 0 then
                redis.call('set', KEYS[2], '1', 'EX', tonumber(ARGV[3]))
                redis.call('del', KEYS[1])
                return 0
            end
            return remaining
            """,
            Long.class
    );

    private final StringRedisTemplate redis;

    public RedisIntentosCodigoEntregaAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long segundosBloqueado(UUID domicilioId) {
        try {
            Long ttl = redis.getExpire(lockKey(domicilioId));
            return ttl == null || ttl < 0 ? 0 : ttl;
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public int registrarFallo(UUID domicilioId) {
        try {
            Long remaining = redis.execute(REGISTRAR_FALLO,
                    List.of(attemptsKey(domicilioId), lockKey(domicilioId)),
                    Long.toString(VENTANA_FALLOS.toSeconds()),
                    Integer.toString(MAXIMO_FALLOS),
                    Long.toString(BLOQUEO.toSeconds()));
            return remaining == null ? 0 : remaining.intValue();
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void limpiar(UUID domicilioId) {
        try {
            redis.delete(List.of(attemptsKey(domicilioId), lockKey(domicilioId)));
        } catch (DataAccessException exception) {
            // La entrega ya quedó confirmada; las claves expiran solas.
            log.warn("No se pudieron limpiar los intentos del código del domicilio {}", domicilioId, exception);
        }
    }

    private static String attemptsKey(UUID domicilioId) {
        return "code-att:" + domicilioId;
    }

    private static String lockKey(UUID domicilioId) {
        return "code-lock:" + domicilioId;
    }

    private static DomainException unavailable(DataAccessException exception) {
        log.error("Redis no disponible al validar los intentos del código de entrega", exception);
        return new DomainException("EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible validar el código en este momento. Inténtalo de nuevo.");
    }
}
