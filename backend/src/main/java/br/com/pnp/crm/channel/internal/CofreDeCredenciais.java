package br.com.pnp.crm.channel.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra e decifra credenciais de canal.
 *
 * <p><b>AES-256-GCM e não AES-CBC:</b> GCM é autenticado. Sem autenticação, um
 * atacante com acesso de escrita ao banco pode alterar bytes do texto cifrado
 * e a aplicação decifraria lixo sem perceber. Com GCM, a alteração é detectada
 * na decifragem e a operação falha.
 *
 * <p><b>Chave separada da aplicação.</b> {@code CHANNEL_SECRET_KEY} não é o
 * {@code APP_PEPPER} nem a {@code JWT_SIGNING_KEY}. Reaproveitar uma delas
 * faria um único vazamento comprometer senhas, sessões e credenciais de
 * integração de uma vez.
 *
 * <p><b>O que isto não protege:</b> quem tem execução no servidor lê a
 * variável de ambiente e decifra tudo. A proteção é contra vazamento de
 * <i>dump do banco</i>, que é o incidente comum.
 */
@Component
class CofreDeCredenciais {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_NONCE_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_CHAVE_BYTES = 32;

    /** Versão atual da chave. Gravada em cada linha, para permitir rotação. */
    static final int VERSAO_CHAVE_ATUAL = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec chave;

    CofreDeCredenciais(@Value("${app.security.channel-secret-key}") String chaveBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(chaveBase64 == null ? "" : chaveBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "CHANNEL_SECRET_KEY precisa estar em base64.", e);
        }
        if (bytes.length != TAMANHO_CHAVE_BYTES) {
            // Falha na subida, não no primeiro envio. Uma chave curta produz
            // criptografia que funciona e não protege.
            throw new IllegalStateException(
                    "CHANNEL_SECRET_KEY precisa ter exatamente 32 bytes decodificados (AES-256).");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    /**
     * @return base64 de {@code nonce || ciphertext || tag}
     */
    String cifrar(String valorEmClaro) {
        try {
            byte[] nonce = new byte[TAMANHO_NONCE_BYTES];
            // Nonce novo a cada cifragem. Reusar nonce em GCM é a falha
            // clássica do modo: com dois textos sob o mesmo nonce, a chave de
            // autenticação pode ser recuperada.
            RANDOM.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, nonce));
            byte[] cifrado = cipher.doFinal(valorEmClaro.getBytes(StandardCharsets.UTF_8));

            byte[] saida = new byte[nonce.length + cifrado.length];
            System.arraycopy(nonce, 0, saida, 0, nonce.length);
            System.arraycopy(cifrado, 0, saida, nonce.length, cifrado.length);
            return Base64.getEncoder().encodeToString(saida);

        } catch (GeneralSecurityException e) {
            // Sem o valor em claro na mensagem, nem por acidente.
            throw new IllegalStateException("Falha ao cifrar credencial de canal.", e);
        }
    }

    String decifrar(String armazenado) {
        try {
            byte[] bytes = Base64.getDecoder().decode(armazenado);
            byte[] nonce = new byte[TAMANHO_NONCE_BYTES];
            System.arraycopy(bytes, 0, nonce, 0, TAMANHO_NONCE_BYTES);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, nonce));
            byte[] claro = cipher.doFinal(
                    bytes, TAMANHO_NONCE_BYTES, bytes.length - TAMANHO_NONCE_BYTES);

            return new String(claro, StandardCharsets.UTF_8);

        } catch (GeneralSecurityException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalStateException("Falha ao decifrar credencial de canal.", e);
        }
    }
}
