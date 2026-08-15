package br.com.pnp.crm.report.internal;

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

/** HMAC exclusivo de exportacoes; nao compartilha chave com JWT ou midia. */
@Component
class ReportExportSigner {

    private final SecretKeySpec chave;

    ReportExportSigner(@Value("${app.security.report-export-signing-key}") String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("REPORT_EXPORT_SIGNING_KEY precisa estar em base64.", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("REPORT_EXPORT_SIGNING_KEY precisa ter ao menos 32 bytes.");
        }
        this.chave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    String assinar(UUID tenantId, UUID exportId, UUID userId, long expiraEmEpoch) {
        return HexFormat.of().formatHex(mac(mensagem(tenantId, exportId, userId, expiraEmEpoch)));
    }

    boolean conferir(UUID tenantId, UUID exportId, UUID userId,
                     long expiraEmEpoch, String apresentada) {
        if (expiraEmEpoch < Instant.now().getEpochSecond() || apresentada == null) return false;
        byte[] recebida;
        try {
            recebida = HexFormat.of().parseHex(apresentada);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(
                mac(mensagem(tenantId, exportId, userId, expiraEmEpoch)), recebida);
    }

    private byte[] mac(String mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chave);
            return mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC de exportacao indisponivel.", e);
        }
    }

    private static String mensagem(UUID tenantId, UUID exportId, UUID userId, long expiraEmEpoch) {
        return tenantId + ":" + exportId + ":" + userId + ":" + expiraEmEpoch;
    }
}
