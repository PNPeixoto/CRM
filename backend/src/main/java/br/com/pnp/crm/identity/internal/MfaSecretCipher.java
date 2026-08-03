package br.com.pnp.crm.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** Cifra autenticada dos segredos TOTP com chave externa e rotacionável. */
@Component
class MfaSecretCipher {

    static final int KEY_VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    MfaSecretCipher(@Value("${app.security.mfa-secret-key}") String encodedKey) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedKey == null ? "" : encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MFA_SECRET_KEY precisa estar em base64.", exception);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "MFA_SECRET_KEY precisa ter exatamente 32 bytes decodificados (AES-256).");
        }
        key = new SecretKeySpec(bytes, "AES");
    }

    String encrypt(String clearText) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, output, 0, nonce.length);
            System.arraycopy(ciphertext, 0, output, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha ao cifrar segredo MFA.", exception);
        }
    }

    String decrypt(String stored) {
        try {
            byte[] input = Base64.getDecoder().decode(stored);
            if (input.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("valor cifrado curto");
            }
            byte[] nonce = java.util.Arrays.copyOf(input, NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(input, NONCE_BYTES, input.length - NONCE_BYTES),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Falha ao decifrar segredo MFA.", exception);
        }
    }
}
