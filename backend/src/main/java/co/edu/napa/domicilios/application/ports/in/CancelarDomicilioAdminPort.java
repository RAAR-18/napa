package co.edu.napa.domicilios.application.ports.in;

import java.util.UUID;

/** RF-13: el administrador cancela un domicilio no entregado, con un motivo; su pedido también se cancela. */
public interface CancelarDomicilioAdminPort {
    Cancelacion cancelar(UUID administradorId, UUID domicilioId, String motivo);

    record Cancelacion(UUID id, String estado, String motivoCancelacion, Pedido pedido) {}

    record Pedido(UUID id, String estado) {}
}
