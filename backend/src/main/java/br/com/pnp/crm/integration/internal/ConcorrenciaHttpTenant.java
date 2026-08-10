package br.com.pnp.crm.integration.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
class ConcorrenciaHttpTenant {

    private final ConcurrentHashMap<UUID, Semaphore> porTenant = new ConcurrentHashMap<>();
    private final int limite;

    ConcorrenciaHttpTenant(
            @Value("${app.http-connector.max-concurrent-per-tenant:4}") int limite) {
        this.limite = Math.clamp(limite, 1, 20);
    }

    Licenca tentar(UUID tenantId) {
        Semaphore semaforo = porTenant.computeIfAbsent(
                tenantId, ignorado -> new Semaphore(limite));
        return semaforo.tryAcquire() ? semaforo::release : null;
    }

    @FunctionalInterface
    interface Licenca extends AutoCloseable {
        @Override
        void close();
    }
}
