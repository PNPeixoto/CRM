package br.com.pnp.crm.task.api;

public interface TaskMetrics {

    ResumoDeTarefas resumo();

    record ResumoDeTarefas(long abertas, long atrasadas) {
    }
}
