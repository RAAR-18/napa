package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Envía el código por SMS con SMSReactor. Si el SMS no sale (por ejemplo, se acabaron los créditos
 * del día) y el respaldo está activo, el código del registro se muestra como imagen en la app;
 * la recuperación y el cambio de celular nunca usan ese respaldo.
 */
@Component
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "smsreactor")
public class SmsReactorVerificacionAdapter extends CodigoPropioVerificacionAdapter {
    private static final Logger log = LoggerFactory.getLogger(SmsReactorVerificacionAdapter.class);

    private final SmsReactorClient client;
    private final boolean respaldoImagen;

    public SmsReactorVerificacionAdapter(
            StringRedisTemplate redisTemplate,
            SmsReactorClient client,
            @Value("${napa.verification.smsreactor.respaldo-imagen:true}") boolean respaldoImagen
    ) {
        super(redisTemplate);
        this.client = client;
        this.respaldoImagen = respaldoImagen;
    }

    @Override
    protected boolean entregar(String celular, PropositoVerificacion proposito, String codigo) {
        try {
            // Sin tildes ni Ñ: así cabe en un solo SMS (GSM-7, 160 caracteres) y cuesta un crédito.
            client.enviar(celular, "Napa: tu codigo de verificacion es " + codigo
                    + ". Vence en 10 minutos. No lo compartas.");
            return true;
        } catch (SmsReactorClient.EnvioFallidoException exception) {
            if (respaldoImagen && proposito == PropositoVerificacion.REGISTRO) {
                log.warn("SMS de registro no enviado; el código se mostrará como imagen: {}", exception.getMessage());
                return false;
            }
            log.error("SMS de verificación no enviado ({}): {}", proposito, exception.getMessage());
            throw noDisponible("No pudimos enviar el código por SMS. Inténtalo de nuevo más tarde.");
        }
    }
}
