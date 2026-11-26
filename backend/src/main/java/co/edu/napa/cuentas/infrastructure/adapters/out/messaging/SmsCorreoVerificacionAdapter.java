package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Ñapa genera el código (Redis: 10 minutos, 5 intentos) y lo entrega por el canal que elige la
 * persona: SMS con SMSReactor o correo con Brevo. WHATSAPP, el canal por defecto anterior, se envía por SMS.
 */
@Component
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "sms-correo")
public class SmsCorreoVerificacionAdapter implements VerificarCelularPort {
    private static final Logger log = LoggerFactory.getLogger(SmsCorreoVerificacionAdapter.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "auth:verification:codigo:";
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final RedisScript<Long> SAVE_CODE = new DefaultRedisScript<>(
            """
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', '0', 'generation', ARGV[3])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            return 1
            """, Long.class);
    private static final RedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>(
            """
            if redis.call('TTL', KEYS[1]) < 0 then redis.call('DEL', KEYS[1]); return 0 end
            if redis.call('HGET', KEYS[1], 'code') ~= ARGV[1] then return 0 end
            if (redis.call('HGET', KEYS[1], 'generation') or '') ~= ARGV[2] then return 0 end
            return redis.call('DEL', KEYS[1])
            """, Long.class);
    private static final RedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            """
            if redis.call('TTL', KEYS[1]) < 0 then redis.call('DEL', KEYS[1]); return -1 end
            if redis.call('HGET', KEYS[1], 'code') ~= ARGV[2] then return -1 end
            if (redis.call('HGET', KEYS[1], 'generation') or '') ~= ARGV[3] then return -1 end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[1]) then
                redis.call('DEL', KEYS[1])
                return 0
            end
            return tonumber(ARGV[1]) - attempts
            """, Long.class);
    private static final RedisScript<Long> RESERVE_QUOTA = new DefaultRedisScript<>(
            """
            local usados = tonumber(redis.call('GET', KEYS[1]) or '0')
            if usados >= tonumber(ARGV[1]) then return 0 end
            local nuevo = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) end
            return nuevo
            """, Long.class);
    private static final RedisScript<Long> RELEASE_QUOTA = new DefaultRedisScript<>(
            """
            local usados = tonumber(redis.call('GET', KEYS[1]) or '0')
            if usados > 0 then return redis.call('DECR', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SmsGateClient sms;
    private final BrevoCorreoClient correo;
    private final SecureRandom random = new SecureRandom();
    private final int limiteDiarioSms;
    private final int limiteDiarioCorreo;
    private final Clock clock;

    @Autowired
    public SmsCorreoVerificacionAdapter(
            StringRedisTemplate redisTemplate,
            SmsGateClient sms,
            BrevoCorreoClient correo,
            @Value("${napa.verification.limite-diario-sms:25}") int limiteDiarioSms,
            @Value("${napa.verification.limite-diario-correo:300}") int limiteDiarioCorreo
    ) {
        this(redisTemplate, sms, correo, limiteDiarioSms, limiteDiarioCorreo, Clock.system(ZONA));
    }

    SmsCorreoVerificacionAdapter(
            StringRedisTemplate redisTemplate,
            SmsGateClient sms,
            BrevoCorreoClient correo,
            int limiteDiarioSms,
            int limiteDiarioCorreo,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.sms = sms;
        this.correo = correo;
        this.limiteDiarioSms = limiteDiarioSms;
        this.limiteDiarioCorreo = limiteDiarioCorreo;
        this.clock = clock;
    }

    @Override
    public EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal) {
        return iniciar(celular, proposito, canal, null);
    }

    @Override
    public EnvioVerificacion iniciar(
            String celular,
            PropositoVerificacion proposito,
            CanalVerificacion canal,
            String destinoCorreo
    ) {
        String codigo = "%06d".formatted(random.nextInt(1_000_000));
        entregar(celular, canal, destinoCorreo, codigo);
        String proveedorId = "codigo-" + UUID.randomUUID();
        String key = key(celular, proposito);
        try {
            Long guardado = redisTemplate.execute(SAVE_CODE, List.of(key), codigo,
                    Long.toString(CODE_TTL.toSeconds()), proveedorId);
            if (!Long.valueOf(1).equals(guardado)) {
                throw unavailable(new IllegalStateException("Redis did not confirm verification storage"));
            }
            return new EnvioVerificacion(proveedorId);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private void entregar(String celular, CanalVerificacion canal, String destinoCorreo, String codigo) {
        boolean porCorreo = canal == CanalVerificacion.CORREO;
        if (porCorreo && (destinoCorreo == null || destinoCorreo.isBlank())) {
            throw new DomainException("VALIDATION_ERROR", "No hay un correo al cual enviar el código.");
        }
        String cupo = PREFIX + "cupo:" + (porCorreo ? "correo:" : "sms:") + LocalDate.now(clock);
        reservarCupo(cupo, porCorreo ? limiteDiarioCorreo : limiteDiarioSms, porCorreo);
        try {
            if (canal == CanalVerificacion.CORREO) {
                correo.enviarCodigo(destinoCorreo, codigo);
            } else {
                // Sin tildes ni Ñ: así cabe en un solo SMS (GSM-7) y cuesta un crédito.
                sms.enviar(celular, "Napa: tu codigo de verificacion es " + codigo
                        + ". Vence en 10 minutos. No lo compartas.");
            }
        } catch (EnvioFallidoException | DomainException exception) {
            liberarCupo(cupo);
            if (exception instanceof DomainException domain) {
                throw domain;
            }
            log.warn("Código de verificación no enviado por {}", canal);
            throw new DomainException(
                    "EXTERNAL_SERVICE_UNAVAILABLE",
                    canal == CanalVerificacion.CORREO
                            ? "No pudimos enviar el código a tu correo. Inténtalo de nuevo o elige SMS."
                            : "No pudimos enviar el SMS. Inténtalo de nuevo o elige recibirlo por correo."
            );
        }
    }

    /** Aplica el tope diario configurado por Ñapa para cada canal. */
    private void reservarCupo(String key, int limite, boolean porCorreo) {
        Long usados;
        try {
            usados = redisTemplate.execute(RESERVE_QUOTA, List.of(key),
                    Integer.toString(limite), Long.toString(Duration.ofHours(48).toSeconds()));
            if (usados == null) {
                throw unavailable(new IllegalStateException("Redis did not confirm provider quota reservation"));
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
        if (usados <= 0) {
            log.warn("Se agotó el cupo diario de {} ({})", porCorreo ? "correos" : "SMS", limite);
            throw new DomainException(
                    "EXTERNAL_SERVICE_UNAVAILABLE",
                    porCorreo
                            ? "Hoy ya no podemos enviar más correos. Pide el código por SMS o inténtalo mañana."
                            : "Hoy ya no podemos enviar más SMS. Pide el código por correo o inténtalo mañana."
            );
        }
    }

    private void liberarCupo(String key) {
        try {
            redisTemplate.execute(RELEASE_QUOTA, List.of(key));
        } catch (DataAccessException exception) {
            log.warn("No se pudo devolver el cupo diario {}", key, exception);
        }
    }

    @Override
    public VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo) {
        return confirmarConResultado(celular, proposito, codigo).estado();
    }

    @Override
    public VerificacionResultado confirmar(String celular, Enum<?> estado, String codigo) {
        PropositoVerificacion proposito = estado instanceof PropositoVerificacion p
                ? p
                : PropositoVerificacion.valueOf(estado.name());
        return confirmarConResultado(celular, proposito, codigo);
    }

    private VerificacionResultado confirmarConResultado(
            String celular,
            PropositoVerificacion proposito,
            String codigo
    ) {
        String key = key(celular, proposito);
        try {
            List<Object> desafio = redisTemplate.opsForHash().multiGet(key, List.of("code", "generation"));
            if (desafio == null) {
                throw unavailable(new IllegalStateException("Redis returned no verification challenge"));
            }
            Object expected = desafio.isEmpty() ? null : desafio.getFirst();
            String generacion = desafio.size() < 2 || desafio.get(1) == null ? "" : desafio.get(1).toString();
            if (expected == null) {
                return new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0);
            }
            if (codigo != null && MessageDigest.isEqual(
                    expected.toString().getBytes(StandardCharsets.UTF_8),
                    codigo.getBytes(StandardCharsets.UTF_8)
            )) {
                Long consumido = redisTemplate.execute(CONSUME_CODE, List.of(key), expected.toString(), generacion);
                if (consumido == null) {
                    throw unavailable(new IllegalStateException("Redis did not confirm verification consumption"));
                }
                return new VerificacionResultado(
                        consumido == 1 ? VerificacionResultado.Estado.APROBADA : VerificacionResultado.Estado.VENCIDA, 0);

            }
            Long remaining = redisTemplate.execute(RECORD_FAILURE, List.of(key), Integer.toString(MAX_ATTEMPTS), expected.toString(), generacion);
            if (remaining == null) {
                throw unavailable(new IllegalStateException("Redis returned no verification attempt result"));
            }
            if (remaining < 0) {
                return new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0);
            }
            if (remaining == 0) {
                return new VerificacionResultado(VerificacionResultado.Estado.AGOTADA, 0);
            }
            return new VerificacionResultado(VerificacionResultado.Estado.INCORRECTA, remaining.intValue());
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private String key(String celular, PropositoVerificacion proposito) {
        try {
            return PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((celular + ":" + proposito.name()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private DomainException unavailable(Exception exception) {
        log.error("Redis unavailable while processing phone verification", exception);
        return new DomainException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "No fue posible procesar la verificación en este momento."
        );
    }
}
