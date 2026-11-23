package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
    private static final RedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[1]) then
                redis.call('DEL', KEYS[1])
                return 0
            end
            return tonumber(ARGV[1]) - attempts
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final SmsReactorClient sms;
    private final BrevoCorreoClient correo;
    private final SecureRandom random = new SecureRandom();

    public SmsCorreoVerificacionAdapter(
            StringRedisTemplate redisTemplate,
            SmsReactorClient sms,
            BrevoCorreoClient correo
    ) {
        this.redisTemplate = redisTemplate;
        this.sms = sms;
        this.correo = correo;
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
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, Map.of("code", codigo, "attempts", "0"));
            redisTemplate.expire(key, CODE_TTL);
            return new EnvioVerificacion(proveedorId);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private void entregar(String celular, CanalVerificacion canal, String destinoCorreo, String codigo) {
        try {
            if (canal == CanalVerificacion.CORREO) {
                if (destinoCorreo == null || destinoCorreo.isBlank()) {
                    throw new DomainException("VALIDATION_ERROR", "No hay un correo al cual enviar el código.");
                }
                correo.enviarCodigo(destinoCorreo, codigo);
            } else {
                // Sin tildes ni Ñ: así cabe en un solo SMS (GSM-7) y cuesta un crédito.
                sms.enviar(celular, "Napa: tu codigo de verificacion es " + codigo
                        + ". Vence en 10 minutos. No lo compartas.");
            }
        } catch (EnvioFallidoException exception) {
            log.error("Código de verificación no enviado por {}: {}", canal, exception.getMessage());
            throw new DomainException(
                    "EXTERNAL_SERVICE_UNAVAILABLE",
                    canal == CanalVerificacion.CORREO
                            ? "No pudimos enviar el código a tu correo. Inténtalo de nuevo o elige SMS."
                            : "No pudimos enviar el SMS. Inténtalo de nuevo o elige recibirlo por correo."
            );
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
            Object expected = redisTemplate.opsForHash().get(key, "code");
            if (expected == null) {
                return new VerificacionResultado(VerificacionResultado.Estado.VENCIDA, 0);
            }
            if (codigo != null && MessageDigest.isEqual(
                    expected.toString().getBytes(StandardCharsets.UTF_8),
                    codigo.getBytes(StandardCharsets.UTF_8)
            )) {
                return new VerificacionResultado(
                        Boolean.TRUE.equals(redisTemplate.delete(key))
                                ? VerificacionResultado.Estado.APROBADA
                                : VerificacionResultado.Estado.VENCIDA,
                        0
                );
            }
            Long remaining = redisTemplate.execute(RECORD_FAILURE, List.of(key), Integer.toString(MAX_ATTEMPTS));
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
