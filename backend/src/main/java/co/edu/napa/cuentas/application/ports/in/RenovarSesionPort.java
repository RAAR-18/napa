package co.edu.napa.cuentas.application.ports.in;

/** RF-10: cambia un refresh token por un par de tokens nuevo (rotación). */
public interface RenovarSesionPort {
    AuthTokens renovar(String refreshToken);
}