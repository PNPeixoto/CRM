package br.com.pnp.crm.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Operações únicas para segredos aleatórios de sessão e recuperação. */
final class SegredosAleatorios {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private SegredosAleatorios() {
    }

    static String gerarUrlSeguro(int bytes) {
        byte[] valor = new byte[bytes];
        RANDOM.nextBytes(valor);
        return BASE64_URL.encodeToString(valor);
    }

    static byte[] gerarBytes(int bytes) {
        byte[] valor = new byte[bytes];
        RANDOM.nextBytes(valor);
        return valor;
    }

    static String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
