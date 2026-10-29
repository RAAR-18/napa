package co.edu.napa.cuentas.application.ports.in;

/** RF-10: cambia un refresh token por un par nuevo (rotación). El refresh solo sirve en su dispositivo. */
public interface RefrescarSesionPort {
    AuthTokens refrescar(String refreshToken, String deviceId);
}