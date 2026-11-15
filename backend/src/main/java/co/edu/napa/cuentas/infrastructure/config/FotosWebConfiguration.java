package co.edu.napa.cuentas.infrastructure.config;

import co.edu.napa.cuentas.infrastructure.adapters.out.storage.AlmacenFotosLocalAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/** Sirve las fotos de perfil guardadas por {@link AlmacenFotosLocalAdapter} (RF-08). */
@Configuration(proxyBeanMethods = false)
public class FotosWebConfiguration implements WebMvcConfigurer {
    private final AlmacenFotosLocalAdapter almacen;

    public FotosWebConfiguration(AlmacenFotosLocalAdapter almacen) {
        this.almacen = almacen;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = almacen.directorio().resolve("perfiles").toUri().toString();
        registry.addResourceHandler("/media/perfiles/**")
                .addResourceLocations(ubicacion.endsWith("/") ? ubicacion : ubicacion + "/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
