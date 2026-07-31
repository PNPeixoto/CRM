package br.com.pnp.crm.identity.internal;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Bloqueio progressivo de tentativas de login, por conta e por IP.
 *
 * <p><b>Por que Redis e não memória:</b> com duas instâncias atrás do
 * balanceador, cinco tentativas por instância viram dez. Além disso, um mapa
 * em memória só descarta entradas expiradas quando aquela chave é acessada de
 * novo — um atacante variando o login a cada tentativa enche a heap sem nunca
 * disparar a limpeza. O TTL nativo do Redis resolve os dois problemas de uma
 * vez, e o contador expira sozinho mesmo que ninguém volte a consultá-lo.
 *
 * <p><b>Por que os dois eixos:</b> só por conta, um atacante distribui as
 * tentativas entre milhares de logins e nunca é barrado. Só por IP, ele usa
 * uma botnet e cada IP fica abaixo do limite — enquanto uma conta específica
 * apanha de todos os lados. Os dois juntos cobrem os dois ataques.
 *
 * <p><b>O que este serviço deliberadamente não faz:</b> não devolve tempo
 * restante nem número de tentativas ao cliente. Isso informaria ao atacante
 * quanto esperar, e à vítima, nada de útil.
 */
@Service
class BloqueioProgressivoService {

    private static final String PREFIXO_CONTA = "login:bloqueio:conta:";
    private static final String PREFIXO_IP = "login:bloqueio:ip:";

    private static final int TENTATIVAS_ATE_BLOQUEIO_CONTA = 5;
    private static final int TENTATIVAS_ATE_BLOQUEIO_IP = 20;

    private static final Duration JANELA = Duration.ofMinutes(15);

    // Progressivo: o bloqueio dura mais a cada faixa de tentativas excedida,
    // até um teto. Bloqueio fixo é contornado esperando o tempo conhecido;
    // bloqueio que cresce torna a força bruta inviável sem trancar
    // permanentemente a conta de um usuário legítimo que errou a senha.
    private static final Duration BLOQUEIO_BASE = Duration.ofMinutes(1);
    private static final Duration BLOQUEIO_MAXIMO = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    BloqueioProgressivoService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    boolean estaBloqueado(UUID tenantId, String login, String ip) {
        return excedeu(chaveConta(tenantId, login), TENTATIVAS_ATE_BLOQUEIO_CONTA)
                || excedeu(chaveIp(ip), TENTATIVAS_ATE_BLOQUEIO_IP);
    }

    void registrarFalha(UUID tenantId, String login, String ip) {
        incrementar(chaveConta(tenantId, login), TENTATIVAS_ATE_BLOQUEIO_CONTA);
        incrementar(chaveIp(ip), TENTATIVAS_ATE_BLOQUEIO_IP);
    }

    /**
     * Zera os contadores após autenticação bem-sucedida. Só a conta é limpa —
     * o contador do IP permanece, porque um login válido no meio de uma
     * varredura não deve absolver o endereço que a está conduzindo.
     */
    void registrarSucesso(UUID tenantId, String login) {
        redis.delete(chaveConta(tenantId, login));
    }

    private boolean excedeu(String chave, int limite) {
        String valor = redis.opsForValue().get(chave);
        return valor != null && Integer.parseInt(valor) >= limite;
    }

    private void incrementar(String chave, int limite) {
        Long tentativas = redis.opsForValue().increment(chave);
        if (tentativas == null) {
            return;
        }
        redis.expire(chave, tentativas >= limite ? duracaoBloqueio(tentativas, limite) : JANELA);
    }

    private Duration duracaoBloqueio(long tentativas, int limite) {
        long excedentes = tentativas - limite;
        // Dobra a cada tentativa excedente; o shift é limitado para não
        // estourar o long antes de o teto ser aplicado.
        long fator = 1L << Math.min(excedentes, 20);
        Duration calculado = BLOQUEIO_BASE.multipliedBy(fator);
        return calculado.compareTo(BLOQUEIO_MAXIMO) > 0 ? BLOQUEIO_MAXIMO : calculado;
    }

    private String chaveConta(UUID tenantId, String login) {
        // O tenant entra na chave: o mesmo login existe em contas diferentes, e
        // bloquear "peixoto" globalmente deixaria um cliente derrubar o acesso
        // de outro.
        //
        // A normalização é OBRIGATÓRIA e precisa casar com a da busca do
        // usuário. Como o login é encontrado sem distinguir maiúsculas,
        // "peixoto" e "Peixoto" chegam ao mesmo usuário — mas produziriam
        // chaves de Redis diferentes, e o atacante contornaria o bloqueio
        // apenas alternando a grafia a cada tentativa.
        return PREFIXO_CONTA + tenantId + ":" + normalizar(login);
    }

    private String normalizar(String login) {
        return login == null ? "" : login.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String chaveIp(String ip) {
        return PREFIXO_IP + ip;
    }
}
