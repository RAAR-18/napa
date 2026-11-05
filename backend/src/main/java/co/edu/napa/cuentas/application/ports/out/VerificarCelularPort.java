package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;

/** Proveedor que genera, entrega y valida los códigos (Twilio Verify, o el adaptador de consola en desarrollo). */
public interface VerificarCelularPort {
    /** Pide al proveedor que genere y envíe un código; invalida el anterior del mismo celular y propósito. */
    EnvioVerificacion iniciar(String celular, PropositoVerificacion proposito, CanalVerificacion canal);

    /** Devuelve APROBADA, INCORRECTA o VENCIDA. Los intentos y el bloqueo (AGOTADA) los lleva Ñapa. */
    VerificacionResultado.Estado confirmar(String celular, PropositoVerificacion proposito, String codigo);

    record EnvioVerificacion(String proveedorId) {
    }
}