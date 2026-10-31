package co.edu.napa.cuentas.infrastructure.adapters.out.security;

import co.edu.napa.cuentas.application.ports.out.PasswordHashingPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Argon2PasswordHashAdapter implements PasswordHashingPort {
    private final PasswordEncoder passwordEncoder;

    public Argon2PasswordHashAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        return passwordEncoder.matches(password, passwordHash);
    }
}
