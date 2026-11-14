package co.edu.napa.cuentas.infrastructure.adapters.out.storage;

import co.edu.napa.cuentas.application.ports.out.AlmacenFotosPort;
import co.edu.napa.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Fotos de perfil en un volumen local (decisión del plan de RF-08: no hay almacenamiento de objetos en el stack).
 * Se sirven en {@code /media/perfiles/**}; cada foto tiene un nombre nuevo para que las cachés no muestren la vieja.
 */
@Component
public class AlmacenFotosLocalAdapter implements AlmacenFotosPort {
    private static final Logger log = LoggerFactory.getLogger(AlmacenFotosLocalAdapter.class);

    private final Path directorio;
    private final String urlBase;

    public AlmacenFotosLocalAdapter(
            @Value("${napa.fotos.directorio:./data/fotos}") String directorio,
            @Value("${napa.fotos.url-base:}") String urlBase
    ) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    public Path directorio() {
        return directorio;
    }

    @Override
    public String guardar(UUID usuarioId, byte[] contenido, String extension) {
        String nombre = usuarioId + "-" + UUID.randomUUID() + "." + extension;
        Path carpeta = directorio.resolve("perfiles");
        try {
            Files.createDirectories(carpeta);
            Path temporal = Files.createTempFile(carpeta, "subida-", ".tmp");
            Files.write(temporal, contenido);
            Files.move(temporal, carpeta.resolve(nombre), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("No se pudo guardar la foto de perfil", e);
            throw new DomainException("EXTERNAL_SERVICE_UNAVAILABLE", "No pudimos guardar la foto; intenta de nuevo.");
        }
        return urlBase + "/media/perfiles/" + nombre;
    }
}
