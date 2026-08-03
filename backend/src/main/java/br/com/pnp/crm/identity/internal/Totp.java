package br.com.pnp.crm.identity.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Instant;

/** TOTP RFC 6238 (SHA-1, 6 dígitos, janela de 30 segundos). */
final class Totp {

    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final long STEP_SECONDS = 30;

    private Totp() {
    }

    static String newSecret() {
        return encodeBase32(SegredosAleatorios.gerarBytes(20));
    }

    static Verification verify(String secret, String supplied, Instant now, Long lastUsedStep) {
        if (supplied == null || !supplied.matches("\\d{6}")) {
            return Verification.invalid();
        }
        long current = now.getEpochSecond() / STEP_SECONDS;
        for (long candidate = current - 1; candidate <= current + 1; candidate++) {
            if ((lastUsedStep == null || candidate > lastUsedStep)
                    && constantTimeEquals(code(secret, candidate), supplied)) {
                return new Verification(true, candidate);
            }
        }
        return Verification.invalid();
    }

    static String code(String secret, long step) {
        try {
            byte[] counter = new byte[8];
            long value = step;
            for (int index = 7; index >= 0; index--) {
                counter[index] = (byte) value;
                value >>>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(counter);
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA1 indisponível para TOTP.", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                supplied.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static String encodeBase32(byte[] input) {
        StringBuilder output = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte item : input) {
            buffer = (buffer << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5) {
                output.append(BASE32[(buffer >>> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            output.append(BASE32[(buffer << (5 - bits)) & 31]);
        }
        return output.toString();
    }

    private static byte[] decodeBase32(String input) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char raw : input.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            int value = new String(BASE32).indexOf(raw);
            if (value < 0) {
                throw new IllegalArgumentException("Segredo TOTP inválido.");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                output.write((buffer >>> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return output.toByteArray();
    }

    record Verification(boolean valid, long step) {
        static Verification invalid() {
            return new Verification(false, -1);
        }
    }
}
