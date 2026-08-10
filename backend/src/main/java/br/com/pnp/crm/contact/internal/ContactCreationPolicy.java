package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Regra pura da criação repetível; não depende de HTTP, banco ou Spring. */
final class ContactCreationPolicy {

    private ContactCreationPolicy() {
    }

    static String validarChave(String chave) {
        if (chave == null) {
            return null;
        }
        if (!chave.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new RequisicaoInvalidaException(
                    "A chave de idempotência deve ter de 8 a 128 caracteres seguros.");
        }
        return chave;
    }

    static long identificadorDoBloqueio(UUID tenantId, String chave) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(chave.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
