package co.edu.napa.cuentas.application.ports.in;

/** Devuelve el celular de la cuenta que tiene ese correo, para verificarla identificándose por correo. */
public interface BuscarCuentaPorCorreoPort {
    String celularDe(String correo);
}
