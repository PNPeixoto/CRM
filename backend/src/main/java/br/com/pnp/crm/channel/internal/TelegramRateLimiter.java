package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Limite distribuido por tenant e conexao; Redis e compartilhado pelas instancias. */
@Component
class TelegramRateLimiter {

    private final StringRedisTemplate redis;
    private final int limiteTenant;
    private final int limiteConexao;

    TelegramRateLimiter(StringRedisTemplate redis,
                        @Value("${app.providers.telegram.rate-limit-tenant-per-second:30}") int limiteTenant,
                        @Value("${app.providers.telegram.rate-limit-connection-per-second:30}") int limiteConexao) {
        this.redis = redis;
        this.limiteTenant = limiteTenant;
        this.limiteConexao = limiteConexao;
    }

    void exigir(UUID conexaoId) {
        UUID tenantId = TenantContext.obrigatorio();
        long janela = Instant.now().getEpochSecond();
        if (!consumir("telegram:rate:tenant:" + tenantId + ':' + janela, limiteTenant)
                || !consumir("telegram:rate:connection:" + conexaoId + ':' + janela, limiteConexao)) {
            throw EnvioDeMensagemException.temporaria(
                    "Limite local do provedor atingido.", Duration.ofSeconds(1));
        }
    }

    private boolean consumir(String chave, int limite) {
        Long atual = redis.opsForValue().increment(chave);
        if (atual != null && atual == 1L) {
            redis.expire(chave, Duration.ofSeconds(2));
        }
        return atual != null && atual <= limite;
    }
}
