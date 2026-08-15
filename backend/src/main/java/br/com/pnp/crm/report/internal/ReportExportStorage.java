package br.com.pnp.crm.report.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Storage privado local, com AES-256-GCM antes de qualquer escrita em disco. */
@Component
class ReportExportStorage {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final Path raiz;
    private final SecretKeySpec chave;
    private final SecureRandom aleatorio = new SecureRandom();

    ReportExportStorage(
            @Value("${app.report-export.storage-path:./var/report-exports}") String raiz,
            @Value("${app.security.report-export-encryption-key}") String chaveBase64) {
        this.raiz = Path.of(raiz).toAbsolutePath().normalize();
        this.chave = new SecretKeySpec(decodificarChave(chaveBase64), "AES");
    }

    Arquivo armazenar(UUID tenantId, UUID exportId, byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0 || conteudo.length > CsvSeguro.MAX_BYTES) {
            throw new IllegalArgumentException("Conteudo de exportacao fora do limite.");
        }
        String storageKey = chave(tenantId, exportId);
        Path destino = resolver(storageKey);
        Path temporario = destino.resolveSibling(exportId + ".part");
        try {
            Files.createDirectories(destino.getParent());
            byte[] cifrado = cifrar(conteudo, tenantId, exportId);
            Files.write(temporario, cifrado, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            mover(temporario, destino);
            return new Arquivo(storageKey, conteudo.length, sha256(conteudo));
        } catch (IOException | GeneralSecurityException e) {
            apagarSilenciosamente(temporario);
            throw new IllegalStateException("Storage privado de exportacao indisponivel.", e);
        }
    }

    byte[] ler(String storageKey, UUID tenantId, UUID exportId) {
        Path arquivo = resolver(storageKey);
        try {
            byte[] cifrado = Files.readAllBytes(arquivo);
            if (cifrado.length > CsvSeguro.MAX_BYTES + 64) {
                throw new IllegalStateException("Arquivo de exportacao fora do limite.");
            }
            return decifrar(cifrado, tenantId, exportId);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Storage privado de exportacao indisponivel.", e);
        }
    }

    void remover(String storageKey) {
        if (storageKey == null) return;
        try {
            Files.deleteIfExists(resolver(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao expurgar exportacao.", e);
        }
    }

    void removerPorId(UUID tenantId, UUID exportId) {
        remover(chave(tenantId, exportId));
    }

    private byte[] cifrar(byte[] conteudo, UUID tenantId, UUID exportId)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        aleatorio.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(aad(tenantId, exportId));
        byte[] ciphertext = cipher.doFinal(conteudo);
        return ByteBuffer.allocate(1 + nonce.length + ciphertext.length)
                .put(FORMAT_VERSION).put(nonce).put(ciphertext).array();
    }

    private byte[] decifrar(byte[] conteudo, UUID tenantId, UUID exportId)
            throws GeneralSecurityException {
        if (conteudo.length <= 1 + NONCE_BYTES || conteudo[0] != FORMAT_VERSION) {
            throw new GeneralSecurityException("Formato cifrado invalido.");
        }
        byte[] nonce = java.util.Arrays.copyOfRange(conteudo, 1, 1 + NONCE_BYTES);
        byte[] ciphertext = java.util.Arrays.copyOfRange(conteudo, 1 + NONCE_BYTES, conteudo.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(aad(tenantId, exportId));
        return cipher.doFinal(ciphertext);
    }

    private Path resolver(String storageKey) {
        Path alvo = raiz.resolve(storageKey).normalize();
        if (!alvo.startsWith(raiz)) {
            throw new IllegalArgumentException("Caminho de exportacao fora do storage.");
        }
        return alvo;
    }

    private static byte[] aad(UUID tenantId, UUID exportId) {
        return (tenantId + ":" + exportId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String chave(UUID tenantId, UUID exportId) {
        return tenantId + "/" + exportId + ".enc";
    }

    private static byte[] decodificarChave(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
            if (bytes.length != 32) {
                throw new IllegalStateException("REPORT_EXPORT_ENCRYPTION_KEY precisa ter 32 bytes.");
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("REPORT_EXPORT_ENCRYPTION_KEY precisa estar em base64.", e);
        }
    }

    private static String sha256(byte[] conteudo) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
    }

    private static void mover(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void apagarSilenciosamente(Path caminho) {
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException ignored) {
            // O erro original e preservado; expurgo posterior remove eventual orfao.
        }
    }

    record Arquivo(String storageKey, long byteSize, String sha256) {
    }
}
