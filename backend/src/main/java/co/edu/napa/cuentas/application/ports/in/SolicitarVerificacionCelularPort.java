package co.edu.napa.cuentas.application.ports.in;

import co.edu.napa.cuentas.domain.model.EstadoVerificacion;

/** RF-70: envía (o reenvía) el código de verificación del celular. */
public interface SolicitarVerificacionCelularPort {
    void solicitar(String celular, EstadoVerificacion proposito, String ipAddress);
}