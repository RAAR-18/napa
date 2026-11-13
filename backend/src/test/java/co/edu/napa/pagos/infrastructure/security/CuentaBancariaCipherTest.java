package co.edu.napa.pagos.infrastructure.security;

import co.edu.napa.pagos.infrastructure.config.CuentaBancariaCipherProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.UUID;

class CuentaBancariaCipherTest {
    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Test
    void encryptsWithRandomNonceAndDecryptsWithoutStoringPlaintext() {
        CuentaBancariaCipher cipher = cipher(TEST_KEY);

        String first = cipher.encrypt("00001234", OWNER_ID);
        String second = cipher.encrypt("00001234", OWNER_ID);

        assertTrue(first.startsWith("v1."));
        assertNotEquals(first, second);
        assertNotEquals("00001234", first);
        assertEquals("00001234", cipher.decrypt(first, OWNER_ID));
    }

    @Test
    void rejectsTamperedCiphertext() {
        CuentaBancariaCipher cipher = cipher(TEST_KEY);
        String encrypted = cipher.encrypt("123456", OWNER_ID);
        String[] parts = encrypted.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? "B" : "A") + parts[2].substring(1);
        String tampered = String.join(".", parts);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered, OWNER_ID));
    }

    @Test
    void ciphertextCannotBeMovedBetweenSellers() {
        CuentaBancariaCipher cipher = cipher(TEST_KEY);
        String encrypted = cipher.encrypt("123456", OWNER_ID);

        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(encrypted, UUID.randomUUID()));
    }

    @Test
    void requiresAValidThirtyTwoByteKey() {
        assertThrows(IllegalArgumentException.class, () -> cipher("bm90LTI1Ni1iaXQ="));
    }

    private static CuentaBancariaCipher cipher(String key) {
        return new CuentaBancariaCipher(new CuentaBancariaCipherProperties(key));
    }
}
