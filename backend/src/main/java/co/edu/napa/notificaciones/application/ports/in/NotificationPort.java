package co.edu.napa.notificaciones.application.ports.in;

import co.edu.napa.notificaciones.application.ports.out.NotificationPage;
import co.edu.napa.notificaciones.domain.model.Notificacion;

import java.util.List;
import java.util.UUID;

public interface NotificationPort {
    NotificationPage listar(UUID usuarioId, Boolean leida, int page, int size, String order);

    Notificacion abrir(UUID usuarioId, UUID notificacionId);

    /** Cuántas notificaciones no ha leído el usuario (insignia de la app). */
    long contarNoLeidas(UUID usuarioId);

    List<PreferenceStatus> listarPreferencias(UUID usuarioId);

    List<PreferenceStatus> actualizarPreferencias(UUID usuarioId, List<PreferenceUpdate> updates);

    record PreferenceUpdate(String tipo, Boolean habilitada) {
    }

    record PreferenceStatus(String tipo, boolean habilitada) {
    }
}
