package co.edu.napa.shared.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class WebCorsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=file:src/main/resources/application.yml")
            .withUserConfiguration(WebCorsConfiguration.class)
            .withBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new);

    @Test
    void defaultConfigurationDoesNotAllowCrossOriginBrowserAccess() {
        runner.run(context -> assertThat(context.getBean(UrlBasedCorsConfigurationSource.class)
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/register"))
                .checkOrigin("http://localhost:8081")).isNull());
    }

    @Test
    void productionDoesNotInheritLocalDevelopmentOrigins() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context ->
                assertThat(context.getBean(UrlBasedCorsConfigurationSource.class)
                        .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/register"))
                        .checkOrigin("http://localhost:8081")).isNull());
    }

    @Test
    void consoleProfileAllowsBothLocalBrowserHostnames() {
        runner.withPropertyValues("spring.profiles.active=otp-console").run(context -> {
            var configuration = context.getBean(UrlBasedCorsConfigurationSource.class)
                    .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/register"));
            assertThat(configuration.checkOrigin("http://localhost:8081")).isEqualTo("http://localhost:8081");
            assertThat(configuration.checkOrigin("http://127.0.0.1:8081")).isEqualTo("http://127.0.0.1:8081");
            assertThat(configuration.checkOrigin("https://untrusted.example")).isNull();
        });
    }

    @Test
    void explicitOriginsReplaceTheDevelopmentDefaults() {
        runner.withPropertyValues("spring.profiles.active=otp-console",
                "NAPA_WEB_ALLOWED_ORIGINS=http://192.168.1.25:8081, http://localhost:8082").run(context -> {
            var configuration = context.getBean(UrlBasedCorsConfigurationSource.class)
                    .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/register"));
            assertThat(configuration.checkOrigin("http://192.168.1.25:8081"))
                    .isEqualTo("http://192.168.1.25:8081");
            assertThat(configuration.checkOrigin("http://localhost:8082")).isEqualTo("http://localhost:8082");
            assertThat(configuration.checkOrigin("http://localhost:8081")).isNull();
        });
    }
}
