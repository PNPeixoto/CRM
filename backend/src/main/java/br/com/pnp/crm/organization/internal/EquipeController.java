package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.EquipeAlterada;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quem responde a quem.
 *
 * <p>É o que dá sentido ao alcance {@code TEAM}: o papel diz <b>o que</b> a
 * pessoa pode fazer, e a equipe diz <b>sobre quem</b>. Sem composição, um papel
 * concedido em alcance de equipe enxerga apenas o próprio responsável — o que é
 * correto e inútil.
 *
 * <p>Exige {@code organization.manage} no tenant. Equipe não tem responsável por
 * registro, e aceitar alcance próprio aqui deixaria alguém montar a hierarquia
 * em que ele mesmo é o gestor de todo mundo.
 */
@RestController
@RequestMapping("/api/organizacao/equipes")
class EquipeController {

    private final EquipeRepository equipes;
    private final PapelRepository papeis;
    private final Autorizacao autorizacao;
    private final ApplicationEventPublisher eventos;

    EquipeController(EquipeRepository equipes, PapelRepository papeis,
                     Autorizacao autorizacao, ApplicationEventPublisher eventos) {
        this.equipes = equipes;
        this.papeis = papeis;
        this.autorizacao = autorizacao;
        this.eventos = eventos;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<List<EquipeResponse>> listar() {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        Map<UUID, List<UUID>> composicao = equipes.composicao();
        Map<UUID, PapelRepository.Membro> porUsuario = papeis.membros().stream()
                .collect(Collectors.toMap(PapelRepository.Membro::usuarioId, membro -> membro,
                        (a, b) -> a));

        List<EquipeResponse> resposta = composicao.entrySet().stream()
                .filter(entrada -> porUsuario.containsKey(entrada.getKey()))
                .map(entrada -> new EquipeResponse(
                        entrada.getKey(),
                        porUsuario.get(entrada.getKey()).nome(),
                        entrada.getValue().stream()
                                .filter(porUsuario::containsKey)
                                .map(id -> new LideradoResponse(id, porUsuario.get(id).nome(),
                                        porUsuario.get(id).login()))
                                .toList()))
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(resposta);
    }

    @PostMapping("/{gestorId}/membros")
    @Transactional
    ResponseEntity<Void> incluir(@PathVariable UUID gestorId,
                                 @Valid @RequestBody IncluirRequest requisicao) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);
        exigirMembroDoTenant(gestorId);
        exigirMembroDoTenant(requisicao.usuarioId());

        if (gestorId.equals(requisicao.usuarioId())) {
            throw new ComposicaoDeEquipeInvalidaException(
                    "Ninguém responde a si mesmo.");
        }
        // O ciclo de dois não trava a consulta — a resolução é de um nível — mas
        // produz duas pessoas se enxergando sem que nenhuma seja gestora de
        // fato, e ninguém consegue explicar isso olhando o organograma.
        if (equipes.lidera(requisicao.usuarioId(), gestorId)) {
            throw new ComposicaoDeEquipeInvalidaException(
                    "Esta pessoa já é gestora de quem você está indicando como gestor.");
        }
        if (equipes.lidera(gestorId, requisicao.usuarioId())) {
            // Idempotente: repetir a inclusão não é erro, e o índice parcial
            // único recusaria com uma mensagem que não ajuda ninguém.
            return ResponseEntity.noContent().build();
        }

        UUID autor = autorizacao.usuarioCorrente();
        equipes.incluir(gestorId, requisicao.usuarioId(), autor);
        eventos.publishEvent(new EquipeAlterada(TenantContext.obrigatorio(), autor,
                gestorId, requisicao.usuarioId(), EquipeAlterada.Mudanca.INCLUIDO));

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{gestorId}/membros/{usuarioId}")
    @Transactional
    ResponseEntity<Void> remover(@PathVariable UUID gestorId, @PathVariable UUID usuarioId) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        UUID autor = autorizacao.usuarioCorrente();
        if (equipes.encerrar(gestorId, usuarioId, autor) == 0) {
            throw new RecursoNaoEncontradoException("Composição de equipe");
        }
        eventos.publishEvent(new EquipeAlterada(TenantContext.obrigatorio(), autor,
                gestorId, usuarioId, EquipeAlterada.Mudanca.REMOVIDO));

        return ResponseEntity.noContent().build();
    }

    /**
     * Só quem tem vínculo ativo entra numa equipe.
     *
     * <p>A FK garante que o usuário é do tenant; isto garante que ele está
     * ativo. Equipe montada com gente desligada infla o alcance do gestor com
     * registros que ninguém mais mantém.
     */
    private void exigirMembroDoTenant(UUID usuarioId) {
        Set<UUID> ativos = papeis.membros().stream()
                .map(PapelRepository.Membro::usuarioId)
                .collect(Collectors.toSet());
        if (!ativos.contains(usuarioId)) {
            throw new RecursoNaoEncontradoException("Membro");
        }
    }

    record EquipeResponse(UUID gestorId, String gestorNome, List<LideradoResponse> liderados) {
        EquipeResponse {
            liderados = List.copyOf(liderados);
        }
    }

    record LideradoResponse(UUID usuarioId, String nome, String login) {
    }

    record IncluirRequest(@NotNull UUID usuarioId) {
    }
}
