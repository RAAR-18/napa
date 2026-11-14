package co.edu.napa.domicilios.domain.event;

import java.util.UUID;

/** Actor de los eventos que emite el sistema sin intervención de un usuario (tareas programadas). */
public final class Actores {
    public static final UUID SISTEMA = new UUID(0L, 0L);

    private Actores() {
    }
}
