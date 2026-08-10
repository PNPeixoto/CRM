package br.com.pnp.crm.integration.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** AES-256-GCM com AAD vinculando o segredo ao tenant e ao conector. */
@Component
class CofreSegredosHttp {

    static final int VERSAO_CHAVE = 1;
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec chave;

    CofreSegredosHttp(
            @Value("${app.security.http-connector-secret-key}") String chaveBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(chaveBase64 == null ? "" : chaveBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "HTTP_CONNECTOR_SECRET_KEY precisa estar em base64.", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "HTTP_CONNECTOR_SECRET_KEY precisa ter exatamente 32 bytes decodificados.");
        }
        chave = new SecretKeySpec(bytes, "AES");
    }

    String cifrar(UUID tenantId, UUID connectorId, String claro) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(tenantId, connectorId));
            byte[] cifrado = cipher.doFinal(claro.getBytes(StandardCharsets.UTF_8));
            byte[] saida = new byte[nonce.length + cifrado.length];
            System.arraycopy(nonce, 0, saida, 0, nonce.length);
            System.arraycopy(cifrado, 0, saida, nonce.length, cifrado.length);
            return Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar segredo do conector HTTP.");
        }
    }

    String decifrar(UUID tenantId, UUID connectorId, String armazenado) {
        try {
            byte[] entrada = Base64.getDecoder().decode(armazenado);
            if (entrada.length <= NONCE_BYTES) throw new IllegalArgumentException("curto");
            byte[] nonce = java.util.Arrays.copyOf(entrada, NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(tenantId, connectorId));
            return new String(cipher.doFinal(
                    entrada, NONCE_BYTES, entrada.length - NONCE_BYTES),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao decifrar segredo do conector HTTP.");
        }
    }

    private static byte[] aad(UUID tenantId, UUID connectorId) {
        return (tenantId + ":" + connectorId + ":http-connector:v1")
                .getBytes(StandardCharsets.UTF_8);
    }
}
