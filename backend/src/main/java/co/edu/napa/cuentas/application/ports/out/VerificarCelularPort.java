package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.EstadoVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;

/** Proveedor que genera, entrega y valida los códigos (Twilio Verify, o el adaptador de consola en desarrollo). */
public interface VerificarCelularPort {
    /** Pide al proveedor que genere y envíe un código; invalida el anterior del mismo celular y propósito. */
    EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal);

    /** Compatibilidad con la versión anterior del dominio, que usaba EstadoVerificacion. */
    default void solicitar(String celular, Enum<?> estado, String ipAddress) {
        PropositoVerificacion proposito = estado instanceof PropositoVerificacion p
                ? p
                : PropositoVerificacion.valueOf(((EstadoVerificacion) estado).name());
        iniciar(celular, proposito, CanalVerificacion.WHATSAPP);
    }

    /** Devuelve APROBADA, INCORRECTA o VENCIDA. Los intentos y el bloqueo (AGOTADA) los lleva Ñapa. */
    VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo);

    default VerificacionResultado confirmar(String celular, Enum<?> estado, String codigo) {
        PropositoVerificacion proposito = estado instanceof PropositoVerificacion p
                ? p
                : PropositoVerificacion.valueOf(((EstadoVerificacion) estado).name());
        return new VerificacionResultado(confirmar(celular, proposito, codigo), 0);
    }

    record EnvioVerificacion(String proveedorId) {
    }
}