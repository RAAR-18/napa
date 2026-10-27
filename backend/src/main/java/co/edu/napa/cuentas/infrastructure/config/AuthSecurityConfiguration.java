package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.shared.infrastructure.idempotency.IdempotencyFilter;
import co.edu.napa.shared.infrastructure.web.ApiError;
import co.edu.napa.shared.infrastructure.web.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Configuration
public class AuthSecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SessionVersionFilter sessionVersionFilter,
            IdempotencyFilter idempotencyFilter,
            ObjectMapper objectMapper,
            Clock clock
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/registro",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/verificaciones-celular",
                                "/api/v1/auth/verificaciones-celular/**",
                                "/api/v1/auth/recuperar-contrasena"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(
                                        request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
                                        response,
                                        objectMapper,
                                        clock,
                                        HttpStatus.UNAUTHORIZED,
                                        "UNAUTHORIZED",
                                        "Debes iniciar sesión para continuar."
                                )))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(
                                        request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
                                        response,
                                        objectMapper,
                                        clock,
                                        HttpStatus.FORBIDDEN,
                                        "FORBIDDEN",
                                        "No tienes permiso para realizar esta acción."
                                )))
                .addFilterAfter(
                        sessionVersionFilter,
                        org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class
                )
                .addFilterBefore(
                        idempotencyFilter,
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class
                );
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("rol");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SessionVersionFilter sessionVersionFilter(
            co.edu.napa.cuentas.application.ports.out.SessionVersionPort sessionVersions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new SessionVersionFilter(sessionVersions, objectMapper, clock);
    }

    private void writeError(
            Object requestId,
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            Clock clock,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        String traceId = requestId instanceof String value && !value.isBlank()
                ? value
                : "unavailable";
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiError(code, message, Map.of(), traceId, Instant.now(clock))
        );
    }

    @Bean
    FilterRegistrationBean<SessionVersionFilter> sessionVersionFilterRegistration(SessionVersionFilter filter) {
        FilterRegistrationBean<SessionVersionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
