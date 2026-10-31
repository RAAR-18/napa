package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Adaptador de consola para desarrollo y pruebas (Decisión D12 del plan de fundaciones).
 * Acepta el código fijo "123456" sin llamar a Twilio para no consumir saldo.
 */
@Component
@Primary
public class ConsolaVerificacionAdapter implements VerificarCelularPort {
    private static final Logger log = LoggerFactory.getLogger(ConsolaVerificacionAdapter.class);
    public static final String CODIGO_DEV = "123456";

    @Override
    public void solicitar(String celular, EstadoVerificacion estado, String ipAddress) {
        log.info("[ÑAPA VERIFICACIÓN DEV] Código generado para {}: {} (motivo: {})", celular, CODIGO_DEV, estado);
    }

    @Override
    public VerificacionResultado confirmar(String celular, EstadoVerificacion estado, String codigo) {
        if (CODIGO_DEV.equals(codigo)) {
            log.info("[ÑAPA VERIFICACIÓN DEV] Código {} APROBADO para {}", codigo, celular);
            return new VerificacionResultado(VerificacionResultado.Estado.APROBADA, 5);
        }
        log.warn("[ÑAPA VERIFICACIÓN DEV] Código {} INCORRECTO para {}", codigo, celular);
        return new VerificacionResultado(VerificacionResultado.Estado.INCORRECTA, 4);
    }
}
