package br.com.pnp.crm.shared.api;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Gerador de UUID versão 7 (RFC 9562).
 *
 * <p>O v7 carrega o timestamp em milissegundos nos 48 bits mais significativos,
 * o que o torna crescente no tempo. Isso importa para o índice: o UUID v4 é
 * aleatório e cada inserção cai numa página diferente da B-tree, fragmentando o
 * índice e derrubando a taxa de acerto do cache. O v7 insere sempre à direita,
 * como um BIGSERIAL, sem expor o volume de registros que um sequencial
 * expõe.
 *
 * <p>A geração é feita aqui e não no banco porque o docker-compose fixa
 * postgres:17-alpine e a função {@code uuidv7()} só existe a partir do
 * Postgres 18. Gerar na aplicação também permite conhecer o id antes do INSERT,
 * o que evita ida e volta ao banco para descobrir a chave recém-criada.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int VERSAO_7 = 0x7000;
    private static final long VARIANTE_RFC_9562 = 0x8000000000000000L;

    private UuidV7() {
    }

    public static UUID gerar() {
        byte[] aleatorio = new byte[10];
        RANDOM.nextBytes(aleatorio);

        long timestampMillis = System.currentTimeMillis();

        // 48 bits de timestamp, 4 bits de versão, 12 bits aleatórios.
        long maisSignificativo = (timestampMillis & 0x0000FFFFFFFFFFFFL) << 16
                | VERSAO_7
                | (aleatorio[0] & 0x0FL) << 8
                | (aleatorio[1] & 0xFFL);

        // 2 bits de variante e 62 bits aleatórios.
        long menosSignificativo = VARIANTE_RFC_9562;
        for (int i = 2; i < 10; i++) {
            menosSignificativo |= (aleatorio[i] & 0xFFL) << (8 * (9 - i));
        }
        // Zera os dois bits de variante vindos do aleatório e reaplica o valor
        // correto, senão o UUID gerado não é reconhecido como RFC 9562.
        menosSignificativo = (menosSignificativo & 0x3FFFFFFFFFFFFFFFL) | VARIANTE_RFC_9562;

        return new UUID(maisSignificativo, menosSignificativo);
    }
}
