package co.edu.napa.cuentas.application.ports.out;

import co.edu.napa.cuentas.domain.model.EstadoVerificacion;

public interface VerificarCelularPort {
    void solicitar(String celular, EstadoVerificacion estado, String ipAddress);

    VerificacionResultado confirmar(String celular, EstadoVerificacion estado, String codigo);
}
