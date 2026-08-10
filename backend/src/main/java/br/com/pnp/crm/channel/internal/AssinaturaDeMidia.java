package br.com.pnp.crm.channel.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** HMAC separado de JWT, credenciais e payloads cifrados. */
@Component
class AssinaturaDeMidia {

    private final SecretKeySpec chave;

    AssinaturaDeMidia(@Value("${app.security.media-signing-key}") String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MEDIA_SIGNING_KEY precisa estar em base64.", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("MEDIA_SIGNING_KEY precisa ter ao menos 32 bytes.");
        }
        this.chave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    String assinar(UUID tenantId, UUID mediaId, long expiraEmEpoch) {
        return HexFormat.of().formatHex(mac(mensagem(tenantId, mediaId, expiraEmEpoch)));
    }

    boolean conferir(UUID tenantId, UUID mediaId, long expiraEmEpoch, String apresentada) {
        if (expiraEmEpoch < Instant.now().getEpochSecond() || apresentada == null) return false;
        byte[] recebida;
        try {
            recebida = HexFormat.of().parseHex(apresentada);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(
                mac(mensagem(tenantId, mediaId, expiraEmEpoch)), recebida);
    }

    private byte[] mac(String mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chave);
            return mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC de midia indisponivel.", e);
        }
    }

    private String mensagem(UUID tenantId, UUID mediaId, long expiraEmEpoch) {
        return tenantId + ":" + mediaId + ":" + expiraEmEpoch;
    }
}
