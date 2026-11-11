package co.edu.napa.cuentas.infrastructure.config;

import co.edu.napa.cuentas.application.ports.out.VerificarCelularPort;
import co.edu.napa.cuentas.application.ports.out.VerificacionResultado;
import co.edu.napa.cuentas.domain.model.CanalVerificacion;
import co.edu.napa.cuentas.domain.model.PropositoVerificacion;
import co.edu.napa.cuentas.infrastructure.adapters.out.messaging.ConsolaVerificacionAdapter;
import co.edu.napa.cuentas.infrastructure.adapters.out.messaging.UnavailableVerificarCelularAdapter;
import co.edu.napa.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationProviderConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            // Carga el mismo archivo del README; application.yml de test no lo sustituye.
            .withPropertyValues("spring.config.location=file:src/main/resources/application.yml")
            .withUserConfiguration(Providers.class)
            .withBean(StringRedisTemplate.class, () -> {
                StringRedisTemplate redis = mock(StringRedisTemplate.class);
                when(redis.delete(anyString())).thenReturn(true);
                return redis;
            });

    @Test
    void verificationIsUnavailableByDefaultWithoutAConsoleCode() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(VerificarCelularPort.class);
            VerificarCelularPort provider = context.getBean(VerificarCelularPort.class);
            assertThat(provider).isInstanceOf(UnavailableVerificarCelularAdapter.class);
            DomainException exception = assertThrows(DomainException.class, () ->
                    provider.iniciar("+573001234567", PropositoVerificacion.REGISTRO, CanalVerificacion.WHATSAPP));
            assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", exception.code());
        });
    }

    @Test
    void documentedProfileSelectsRedisConsoleAndUsesTheConfiguredCode() {
        runner.withPropertyValues("spring.profiles.active=otp-console", "NAPA_OTP_CONSOLE_CODE=654321")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VerificarCelularPort.class);
                    VerificarCelularPort provider = context.getBean(VerificarCelularPort.class);
                    assertThat(provider).isInstanceOf(ConsolaVerificacionAdapter.class);
                    assertEquals(VerificacionResultado.Estado.APROBADA,
                            provider.confirmar("+573001234567", PropositoVerificacion.REGISTRO, "654321"));
                });
    }

    @Test
    void consoleProfileRequiresAnExplicitCode() {
        runner.withPropertyValues("spring.profiles.active=otp-console", "NAPA_OTP_CONSOLE_CODE=")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void consoleProfileRejectsMalformedCodes() {
        runner.withPropertyValues("spring.profiles.active=otp-console", "NAPA_OTP_CONSOLE_CODE=12345")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void productionKeepsVerificationUnavailable() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(VerificarCelularPort.class);
            assertThat(context.getBean(VerificarCelularPort.class))
                    .isInstanceOf(UnavailableVerificarCelularAdapter.class);
        });
    }

    @Test
    void consoleCannotBeEnabledInProduction() {
        runner.withPropertyValues("spring.profiles.active=prod,otp-console", "NAPA_OTP_CONSOLE_CODE=654321")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ConsolaVerificacionAdapter.class, UnavailableVerificarCelularAdapter.class})
    static class Providers {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ProviderConsumer providerConsumer(VerificarCelularPort provider) {
            return new ProviderConsumer(provider);
        }
    }

    record ProviderConsumer(VerificarCelularPort provider) {
    }
}
