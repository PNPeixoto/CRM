package br.com.pnp.crm.task.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.task.api.TaskMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
class TaskMetricsImpl implements TaskMetrics {

    private final TaskRepository repository;

    TaskMetricsImpl(TaskRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumoDeTarefas resumo() {
        UUID tenantId = TenantContext.obrigatorio();
        return new ResumoDeTarefas(
                repository.countByTenantIdAndDoneAtIsNullAndDeletedAtIsNull(tenantId),
                // "Atrasada" é vencimento no passado e ainda aberta. Comparado
                // em UTC, que é como o banco guarda — comparar no fuso de
                // exibição faria a contagem mudar conforme quem está olhando.
                repository.countByTenantIdAndDoneAtIsNullAndDueAtLessThanAndDeletedAtIsNull(
                        tenantId, Instant.now()));
    }
}
