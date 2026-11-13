package co.edu.napa.cuentas.application.ports.in;

import java.util.Optional;
import java.util.UUID;

/** Nombre y celular de un usuario, para quien tiene derecho a contactarlo (p. ej. el domiciliario asignado, RF-18). */
public interface ConsultarContactoUsuarioPort {
    Optional<Contacto> consultar(UUID usuarioId);

    record Contacto(String nombre, String celular) {}
}
