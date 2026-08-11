package br.com.pnp.crm.task.internal;

import br.com.pnp.crm.contact.api.ContactLookup;
import br.com.pnp.crm.deal.api.DealLookup;
import br.com.pnp.crm.identity.api.UsuarioLookup;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tarefas")
class TaskController {

    private final TaskRepository repository;
    private final ContactLookup contacts;
    private final DealLookup deals;
    private final UsuarioLookup users;
    private final Autorizacao autorizacao;

    TaskController(TaskRepository repository, ContactLookup contacts,
                   DealLookup deals, UsuarioLookup users, Autorizacao autorizacao) {
        this.repository = repository;
        this.contacts = contacts;
        this.deals = deals;
        this.users = users;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<List<TarefaResponse>> listar(
            @RequestParam(defaultValue = "false") boolean apenasAbertas) {
        Autorizacao.Recorte recorte = autorizacao.recorteDe(Permissao.TASKS_READ);
        List<TaskEntity> tarefas = repository.listar(
                TenantContext.obrigatorio(),
                recorte.todoOTenant(), recorte.responsaveis(), apenasAbertas);
        Map<UUID, UsuarioLookup.UsuarioReference> responsaveis = users.findKnown(
                TenantContext.obrigatorio(), tarefas.stream()
                        .map(TaskEntity::getAssignedUserId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList());
        return ResponseEntity.ok(tarefas.stream()
                .map(tarefa -> paraResposta(
                        tarefa, responsaveis.get(tarefa.getAssignedUserId())))
                .toList());
    }



    @PostMapping
    @Transactional
    ResponseEntity<TarefaResponse> criar(@Valid @RequestBody TarefaRequest requisicao,
                                         @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigir(Permissao.TASKS_WRITE);
        UUID autorId = UUID.fromString(jwt.getSubject());
        TaskEntity tarefa = TaskEntity.nova(TenantContext.obrigatorio(), autorId);
        aplicar(tarefa, requisicao, autorId);
        // Sob alcance próprio não se cria tarefa para outra pessoa: seria
        // criar hoje o que não se poderia ler amanhã.
        autorizacao.exigirSobreRegistro(Permissao.TASKS_WRITE, tarefa.getAssignedUserId());
        return ResponseEntity.ok(paraResposta(repository.save(tarefa)));
    }

    @PutMapping("/{id}")
    @Transactional
    ResponseEntity<TarefaResponse> atualizar(@PathVariable UUID id,
                                             @Valid @RequestBody TarefaRequest requisicao,
                                             @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigir(Permissao.TASKS_WRITE);
        TaskEntity tarefa = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.TASKS_WRITE, tarefa.getAssignedUserId());
        aplicar(tarefa, requisicao, UUID.fromString(jwt.getSubject()));
        // Revalida depois: impede transferir a tarefa para fora do próprio
        // alcance na mesma chamada que a edita.
        autorizacao.exigirSobreRegistro(Permissao.TASKS_WRITE, tarefa.getAssignedUserId());
        return ResponseEntity.ok(paraResposta(tarefa));
    }

    /** Alterna concluída/aberta. Ver o comentário em {@code alternarConclusao}. */
    @PostMapping("/{id}/concluir")
    @Transactional
    ResponseEntity<TarefaResponse> alternarConclusao(@PathVariable UUID id,
                                                     @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigir(Permissao.TASKS_WRITE);
        TaskEntity tarefa = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.TASKS_WRITE, tarefa.getAssignedUserId());
        tarefa.alternarConclusao(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(paraResposta(tarefa));
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigir(Permissao.TASKS_WRITE);
        TaskEntity tarefa = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.TASKS_WRITE, tarefa.getAssignedUserId());
        tarefa.excluir(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    private TaskEntity carregar(UUID id) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(id, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa"));
    }

    private void aplicar(TaskEntity t, TarefaRequest r, UUID autorId) {
        UUID tenantId = TenantContext.obrigatorio();
        UUID responsavelId = autorizacao.responsavelPadrao(
                Permissao.TASKS_WRITE, r.responsavelId());
        autorizacao.exigirSobreRegistro(Permissao.TASKS_WRITE, responsavelId);
        if (responsavelId != null && !users.existsActive(tenantId, responsavelId)) {
            throw new RecursoNaoEncontradoException("Responsável");
        }
        if (r.contatoId() != null) {
            ContactLookup.ContactReference contato = contacts.findReference(tenantId, r.contatoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Contato"));
            autorizacao.exigirSobreRegistro(
                    Permissao.CONTACTS_READ, contato.ownerUserId());
        }
        if (r.oportunidadeId() != null) {
            DealLookup.DealReference oportunidade = deals.findReference(
                            tenantId, r.oportunidadeId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Oportunidade"));
            autorizacao.exigirSobreRegistro(
                    Permissao.DEALS_READ, oportunidade.ownerUserId());
        }
        t.aplicar(r.titulo(), r.descricao(), r.vencimentoEm(),
                responsavelId,
                r.contatoId(), r.oportunidadeId(), autorId);
    }

    private TarefaResponse paraResposta(TaskEntity t) {
        UsuarioLookup.UsuarioReference responsavel = t.getAssignedUserId() == null
                ? null
                : users.findKnown(TenantContext.obrigatorio(), List.of(t.getAssignedUserId()))
                        .get(t.getAssignedUserId());
        return paraResposta(t, responsavel);
    }

    private static TarefaResponse paraResposta(
            TaskEntity t, UsuarioLookup.UsuarioReference responsavel) {
        return new TarefaResponse(t.getId(), t.getTitle(), t.getDescription(), t.getDueAt(),
                t.getDoneAt(), t.getAssignedUserId(), t.getContactId(), t.getDealId(),
                responsavel == null ? null : responsavel.login(),
                responsavel == null ? null : responsavel.nomeCompleto(),
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
            UUID responsavelId, UUID contatoId, UUID oportunidadeId,
            String responsavelLogin, String responsavelNome, Instant criadaEm) {
    }
}
