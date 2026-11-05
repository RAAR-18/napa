package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proveedor falso para desarrollo y pruebas: no envía nada ni gasta mensajes. Acepta un código fijo
 * (napa.verification.console-code, por defecto 123456) dentro de su vigencia. Nunca imprime el código.
 * Se activa con NAPA_VERIFICATION_PROVIDER=console y jamás en el perfil prod.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "console")
public class ConsolaVerificacionAdapter implements VerificarCelularPort {
    private static final Logger log = LoggerFactory.getLogger(ConsolaVerificacionAdapter.class);
    private static final Duration VIGENCIA = Duration.ofMinutes(10);

    private final Clock clock;
    private final String codigoFijo;
    private final Map<String, Instant> pendientes = new ConcurrentHashMap<>();

    public ConsolaVerificacionAdapter(
            Clock clock,
            @Value("${napa.verification.console-code:123456}") String codigoFijo
    ) {
        this.clock = clock;
        this.codigoFijo = codigoFijo;
    }

    @Override
    public EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal) {
        pendientes.put(clave(celular, proposito), Instant.now(clock));
        log.info(
                "[verificación de consola] código simulado por {} a {} (propósito {}); "
                        + "el código válido es el de napa.verification.console-code",
                canal, enmascarar(celular), proposito
        );
        return new EnvioVerificacion("console-" + UUID.randomUUID());
    }

    @Override
    public VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo) {
        String clave = clave(celular, proposito);
        Instant enviadoEn = pendientes.get(clave);
        if (enviadoEn == null) {
            return VerificacionResultado.Estado.VENCIDA;
        }
        if (Instant.now(clock).isAfter(enviadoEn.plus(VIGENCIA))) {
            pendientes.remove(clave);
            return VerificacionResultado.Estado.VENCIDA;
        }
        if (codigoFijo.equals(codigo)) {
            pendientes.remove(clave);
            return VerificacionResultado.Estado.APROBADA;
        }
        return VerificacionResultado.Estado.INCORRECTA;
    }

    private String clave(String celular, PropositoVerificacion proposito) {
        return celular + ":" + proposito.name();
    }

    private String enmascarar(String celular) {
        int largo = celular.length();
        return celular.substring(0, 3) + "*".repeat(Math.max(0, largo - 7)) + celular.substring(largo - 4);
    }
}