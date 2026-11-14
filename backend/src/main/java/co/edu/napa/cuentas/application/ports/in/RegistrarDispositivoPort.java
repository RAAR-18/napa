package co.edu.napa.cuentas.application.ports.in;

import java.util.UUID;

/** RF-38: token push (FCM) del dispositivo con el que el usuario abrió sesión. */
public interface RegistrarDispositivoPort {
    /** Registra el token o renueva su última conexión; repetirlo no duplica. */
    void registrar(UUID usuarioId, String token, String plataforma);

    /** Al cerrar sesión, el dispositivo deja de recibir los avisos de esa cuenta. */
    void darDeBaja(UUID usuarioId, String token);
}
