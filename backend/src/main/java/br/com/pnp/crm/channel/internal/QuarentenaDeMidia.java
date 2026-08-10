package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Storage local fora do web root; nada e servido enquanto estiver em quarentena. */
@Component
class QuarentenaDeMidia {

    static final long LIMITE_BYTES = 20L * 1024 * 1024;

    private final Path raiz;

    QuarentenaDeMidia(@Value("${app.providers.telegram.media-storage-path:./var/media-quarantine}")
                      String raiz) {
        this.raiz = Path.of(raiz).toAbsolutePath().normalize();
    }

    MidiaArmazenada armazenar(UUID tenantId, UUID mediaId, InputStream origem) {
        Path diretorio = raiz.resolve(tenantId.toString()).normalize();
        Path temporario = diretorio.resolve(mediaId + ".part");
        Path destino = diretorio.resolve(mediaId + ".bin");
        validarDentroDaRaiz(diretorio);
        validarDentroDaRaiz(temporario);
        validarDentroDaRaiz(destino);

        try {
            Files.createDirectories(diretorio);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] cabecalho = new byte[16];
            int cabecalhoLido = 0;
            long total = 0;

            try (OutputStream saida = Files.newOutputStream(
                    temporario, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = origem.read(buffer)) != -1) {
                    total += lidos;
                    if (total > LIMITE_BYTES) {
                        throw EnvioDeMensagemException.permanente(
                                "Midia excede o limite de 20 MB.");
                    }
                    if (cabecalhoLido < cabecalho.length) {
                        int copiar = Math.min(lidos, cabecalho.length - cabecalhoLido);
                        System.arraycopy(buffer, 0, cabecalho, cabecalhoLido, copiar);
                        cabecalhoLido += copiar;
                    }
                    digest.update(buffer, 0, lidos);
                    saida.write(buffer, 0, lidos);
                }
            }

            if (total == 0) {
                throw EnvioDeMensagemException.permanente("Midia vazia recusada.");
            }
            String tipo = detectar(cabecalho, cabecalhoLido);
            moverAtomicamente(temporario, destino);
            String chave = raiz.relativize(destino).toString().replace('\\', '/');
            return new MidiaArmazenada(
                    chave, total, HexFormat.of().formatHex(digest.digest()), tipo);
        } catch (EnvioDeMensagemException e) {
            apagarSilenciosamente(temporario);
            throw e;
        } catch (IOException | NoSuchAlgorithmException e) {
            apagarSilenciosamente(temporario);
            throw EnvioDeMensagemException.temporaria(
                    "Falha ao armazenar midia em quarentena.");
        }
    }

    void remover(String storageKey) {
        Path alvo = resolver(storageKey);
        try {
            Files.deleteIfExists(alvo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao expurgar midia da quarentena.", e);
        }
    }

    Path resolver(String storageKey) {
        Path alvo = raiz.resolve(storageKey).normalize();
        validarDentroDaRaiz(alvo);
        return alvo;
    }

    private String detectar(byte[] b, int n) {
        if (n >= 3 && u(b[0]) == 0xff && u(b[1]) == 0xd8 && u(b[2]) == 0xff) return "image/jpeg";
        if (n >= 8 && u(b[0]) == 0x89 && ascii(b, 1, "PNG\r\n\u001a\n")) return "image/png";
        if (n >= 5 && ascii(b, 0, "%PDF-")) return "application/pdf";
        if (n >= 4 && ascii(b, 0, "OggS")) return "audio/ogg";
        if (n >= 12 && ascii(b, 4, "ftyp")) return "video/mp4";
        if (n >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) return "image/webp";
        // Inclui HTML, SVG, executaveis e formatos sem parser aprovado.
        throw EnvioDeMensagemException.permanente(
                "Tipo de midia nao permitido pela politica de seguranca.");
    }

    private boolean ascii(byte[] b, int inicio, String esperado) {
        byte[] bytes = esperado.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (inicio + bytes.length > b.length) return false;
        for (int i = 0; i < bytes.length; i++) {
            if (b[inicio + i] != bytes[i]) return false;
        }
        return true;
    }

    private int u(byte valor) {
        return valor & 0xff;
    }

    private void moverAtomicamente(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino);
        }
    }

    private void validarDentroDaRaiz(Path caminho) {
        if (!caminho.toAbsolutePath().normalize().startsWith(raiz)) {
            throw new IllegalArgumentException("Caminho de midia fora da quarentena.");
        }
    }

    private void apagarSilenciosamente(Path caminho) {
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException ignored) {
            // O erro original e mais importante; limpeza de orfao e operacional.
        }
    }

    record MidiaArmazenada(String storageKey, long tamanho, String sha256, String tipoDetectado) {
    }
}
