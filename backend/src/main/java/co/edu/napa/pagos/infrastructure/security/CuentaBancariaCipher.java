package co.edu.napa.pagos.infrastructure.security;

import co.edu.napa.pagos.infrastructure.config.CuentaBancariaCipherProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Component
public class CuentaBancariaCipher {
    private static final String VERSION = "v1";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public CuentaBancariaCipher(CuentaBancariaCipherProperties properties) {
        Objects.requireNonNull(properties, "properties");
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(properties.claveBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("La clave de cifrado de cuentas debe estar codificada en Base64.", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("La clave de cifrado de cuentas debe tener 32 bytes.");
        }
        key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext, UUID vendedorId) {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(vendedorId, "vendedorId");
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(vendedorId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return VERSION + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo cifrar el número de cuenta.", exception);
        }
    }

    public String decrypt(String encrypted, UUID vendedorId) {
        Objects.requireNonNull(encrypted, "encrypted");
        Objects.requireNonNull(vendedorId, "vendedorId");
        String[] parts = encrypted.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("El número de cuenta tiene un formato de cifrado desconocido.");
        }
        try {
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            if (nonce.length != NONCE_LENGTH) {
                throw new IllegalStateException("El número de cuenta tiene un nonce inválido.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(vendedorId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("No se pudo descifrar el número de cuenta.", exception);
        }
    }
}
