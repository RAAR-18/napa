package co.edu.napa.notificaciones.application.ports.out;

import co.edu.napa.notificaciones.domain.model.TipoNotificacion;

import java.util.Map;
import java.util.UUID;

public interface PreferenciaNotificacionRepositoryPort {
    Map<TipoNotificacion, Boolean> findByUsuario(UUID usuarioId);

    void save(UUID usuarioId, TipoNotificacion tipo, boolean habilitada);
}
