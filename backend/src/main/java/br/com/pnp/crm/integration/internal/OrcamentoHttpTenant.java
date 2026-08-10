package br.com.pnp.crm.integration.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
class OrcamentoHttpTenant {

    private final StringRedisTemplate redis;
    private final int limiteGlobal;

    OrcamentoHttpTenant(
            StringRedisTemplate redis,
            @Value("${app.http-connector.max-requests-per-minute:120}") int limiteGlobal) {
        this.redis = redis;
        this.limiteGlobal = Math.clamp(limiteGlobal, 1, 1000);
    }

    boolean consumir(UUID tenantId, UUID connectorId, int limiteConector) {
        long janela = Instant.now().getEpochSecond() / 60;
        try {
            long tenant = incrementar("crm:http:budget:t:" + tenantId + ":" + janela);
            long conector = incrementar(
                    "crm:http:budget:c:" + tenantId + ":" + connectorId + ":" + janela);
            return tenant <= limiteGlobal && conector <= limiteConector;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private long incrementar(String chave) {
        Long valor = redis.opsForValue().increment(chave);
        if (valor != null && valor == 1) redis.expire(chave, Duration.ofMinutes(2));
        return valor == null ? Long.MAX_VALUE : valor;
    }
}
