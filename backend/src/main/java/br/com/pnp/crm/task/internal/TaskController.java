package br.com.pnp.crm.task.internal;

import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tarefas")
class TaskController {

    private final TaskRepository repository;

    TaskController(TaskRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<List<TarefaResponse>> listar(
            @RequestParam(defaultValue = "false") boolean apenasAbertas) {
        return ResponseEntity.ok(
                repository.listar(TenantContext.obrigatorio(), apenasAbertas).stream()
                        .map(TaskController::paraResposta).toList());
    }

    @PostMapping
    @Transactional
    ResponseEntity<TarefaResponse> criar(@Valid @RequestBody TarefaRequest requisicao,
                                         @AuthenticationPrincipal Jwt jwt) {
        UUID autorId = UUID.fromString(jwt.getSubject());
        TaskEntity tarefa = TaskEntity.nova(TenantContext.obrigatorio(), autorId);
        aplicar(tarefa, requisicao, autorId);
        return ResponseEntity.ok(paraResposta(repository.save(tarefa)));
    }

    @PutMapping("/{id}")
    @Transactional
    ResponseEntity<TarefaResponse> atualizar(@PathVariable UUID id,
                                             @Valid @RequestBody TarefaRequest requisicao,
                                             @AuthenticationPrincipal Jwt jwt) {
        TaskEntity tarefa = carregar(id);
        aplicar(tarefa, requisicao, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(paraResposta(tarefa));
    }

    /** Alterna concluída/aberta. Ver o comentário em {@code alternarConclusao}. */
    @PostMapping("/{id}/concluir")
    @Transactional
    ResponseEntity<TarefaResponse> alternarConclusao(@PathVariable UUID id,
                                                     @AuthenticationPrincipal Jwt jwt) {
        TaskEntity tarefa = carregar(id);
        tarefa.alternarConclusao(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(paraResposta(tarefa));
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        carregar(id).excluir(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    private TaskEntity carregar(UUID id) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(id, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa"));
    }

    private void aplicar(TaskEntity t, TarefaRequest r, UUID autorId) {
        t.aplicar(r.titulo(), r.descricao(), r.vencimentoEm(),
                r.responsavelId(), r.contatoId(), r.oportunidadeId(), autorId);
    }

    private static TarefaResponse paraResposta(TaskEntity t) {
        return new TarefaResponse(t.getId(), t.getTitle(), t.getDescription(), t.getDueAt(),
                t.getDoneAt(), t.getAssignedUserId(), t.getContactId(), t.getDealId(),
                t.getCreatedAt());
    }

    record TarefaRequest(
            @NotBlank(message = "Informe o título.") @Size(max = 200) String titulo,
            @Size(max = 4000) String descricao,
            Instant vencimentoEm,
            UUID responsavelId,
            UUID contatoId,
            UUID oportunidadeId) {
    }

    record TarefaResponse(
            UUID id, String titulo, String descricao, Instant vencimentoEm, Instant concluidaEm,
            UUID responsavelId, UUID contatoId, UUID oportunidadeId, Instant criadaEm) {
    }
}
