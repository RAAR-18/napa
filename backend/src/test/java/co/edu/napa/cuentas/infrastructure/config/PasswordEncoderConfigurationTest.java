package co.edu.napa.cuentas.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderConfigurationTest {
    @Test
    void storesPasswordsAsArgon2idHashes() {
        PasswordEncoder encoder = new PasswordEncoderConfiguration().passwordEncoder();

        String hash = encoder.encode("correct horse battery staple");

        assertNotEquals("correct horse battery staple", hash);
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(encoder.matches("correct horse battery staple", hash));
    }
}
