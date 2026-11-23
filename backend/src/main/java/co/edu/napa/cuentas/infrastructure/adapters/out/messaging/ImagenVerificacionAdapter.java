package co.edu.napa.cuentas.infrastructure.adapters.out.messaging;

import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Proveedor provisional sin SMS: la app muestra el código como imagen. No prueba que la persona
 * tenga el celular, por eso solo atiende el registro; mostrar el código de una recuperación o de un
 * cambio de celular permitiría tomar una cuenta ajena con solo conocer su número.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "napa.verification.provider", havingValue = "imagen")
public class ImagenVerificacionAdapter extends CodigoPropioVerificacionAdapter {
    public ImagenVerificacionAdapter(StringRedisTemplate redisTemplate) {
        super(redisTemplate);
    }

    @Override
    protected boolean entregar(String celular, PropositoVerificacion proposito, String codigo) {
        if (proposito != PropositoVerificacion.REGISTRO) {
            throw noDisponible("Este tipo de verificación aún no está disponible.");
        }
        return false;
    }
}
