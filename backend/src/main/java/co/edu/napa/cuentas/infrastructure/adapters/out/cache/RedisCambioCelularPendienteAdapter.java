package co.edu.napa.cuentas.infrastructure.adapters.out.cache;

import co.edu.napa.cuentas.application.ports.out.CambioCelularPendientePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Clave {@code cambio-cel:{usuarioId}} con vencimiento (plan de RF-08). */
@Component
public class RedisCambioCelularPendienteAdapter implements CambioCelularPendientePort {
    private final StringRedisTemplate redis;

    public RedisCambioCelularPendienteAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void guardar(UUID usuarioId, String celular, Duration vigencia) {
        redis.opsForValue().set(clave(usuarioId), celular, vigencia);
    }

    @Override
    public Optional<String> consultar(UUID usuarioId) {
        return Optional.ofNullable(redis.opsForValue().get(clave(usuarioId)));
    }

    @Override
    public void borrar(UUID usuarioId) {
        redis.delete(clave(usuarioId));
    }

    private static String clave(UUID usuarioId) {
        return "cambio-cel:" + usuarioId;
    }
}
