package co.edu.napa.cuentas.infrastructure.security;

import co.edu.napa.cuentas.application.ports.out.UsuarioRepositoryPort;
import co.edu.napa.cuentas.domain.model.EstadoUsuario;
import co.edu.napa.cuentas.domain.model.RolUsuario;
import co.edu.napa.cuentas.domain.model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private final UsuarioRepositoryPort usuarios;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean enabled;
    private final String name;
    private final String phone;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(
            UsuarioRepositoryPort usuarios,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${napa.auth.admin-bootstrap.enabled:false}") boolean enabled,
            @Value("${napa.auth.admin-bootstrap.name:}") String name,
            @Value("${napa.auth.admin-bootstrap.phone:}") String phone,
            @Value("${napa.auth.admin-bootstrap.email:}") String email,
            @Value("${napa.auth.admin-bootstrap.password:}") String password
    ) {
        this.usuarios = usuarios;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.enabled = enabled;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO bootstrap_state (name, completed_at) VALUES ('admin', NULL) ON CONFLICT (name) DO NOTHING"
        );
        java.sql.Timestamp completedAt = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM bootstrap_state WHERE name = 'admin' FOR UPDATE",
                java.sql.Timestamp.class
        );
        if (completedAt != null) {
            return;
        }
        if (usuarios.existsByRol(RolUsuario.ADMINISTRADOR)) {
            jdbcTemplate.update(
                    "UPDATE bootstrap_state SET completed_at = ? WHERE name = 'admin'",
                    java.sql.Timestamp.from(Instant.now(clock))
            );
            return;
        }
        if (name.isBlank()
                || !phone.matches("^[+][1-9][0-9]{7,14}$")
                || password.codePointCount(0, password.length()) < 8
                || password.codePointCount(0, password.length()) > 128) {
            throw new IllegalStateException(
                    "Admin bootstrap requires NAPA_ADMIN_NAME, an E.164 NAPA_ADMIN_PHONE and an 8-128 character NAPA_ADMIN_PASSWORD"
            );
        }
        Instant now = Instant.now(clock);
        Usuario admin = new Usuario(
                UUID.randomUUID(),
                name.trim(),
                phone,
                email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT),
                passwordEncoder.encode(password),
                RolUsuario.ADMINISTRADOR,
                EstadoUsuario.ACTIVA,
                now,
                now,
                now
        );
        usuarios.save(admin);
        jdbcTemplate.update(
                "UPDATE bootstrap_state SET completed_at = ? WHERE name = 'admin'",
                java.sql.Timestamp.from(now)
        );
    }
}
