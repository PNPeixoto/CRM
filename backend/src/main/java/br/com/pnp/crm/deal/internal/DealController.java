package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.contact.api.ContactLookup;
import br.com.pnp.crm.identity.api.UsuarioLookup;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class DealController {

    private final PipelineRepository funis;
    private final PipelineStageRepository etapas;
    private final DealRepository oportunidades;
    private final FunilPadraoService funilPadrao;
    private final ContactLookup contacts;
    private final UsuarioLookup users;
    private final Autorizacao autorizacao;

    DealController(PipelineRepository funis, PipelineStageRepository etapas,
                   DealRepository oportunidades, FunilPadraoService funilPadrao,
                   ContactLookup contacts, UsuarioLookup users, Autorizacao autorizacao) {
        this.funis = funis;
        this.etapas = etapas;
        this.oportunidades = oportunidades;
        this.funilPadrao = funilPadrao;
        this.contacts = contacts;
        this.users = users;
        this.autorizacao = autorizacao;
    }

    /**
     * Devolve os funis, criando o padrão na primeira chamada. É por isso que
     * este endpoint não é somente-leitura: sem ele, a tela do kanban abriria
     * vazia e sem colunas no primeiro acesso.
     */
    @GetMapping("/funis")
    @Transactional
    ResponseEntity<List<DealDtos.FunilResponse>> listarFunis(@AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigir(Permissao.DEALS_READ);
        funilPadrao.obterOuCriar(UUID.fromString(jwt.getSubject()));

        List<DealDtos.FunilResponse> resposta =
                funis.findByTenantIdAndDeletedAtIsNullOrderByName(TenantContext.obrigatorio())
                        .stream()
                        .map(funil -> new DealDtos.FunilResponse(
                                funil.getId(), funil.getName(), funil.isDefault(),
                                etapasDe(funil.getId())))
                        .toList();

        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/funis/{funilId}/oportunidades")
    @Transactional(readOnly = true)
    ResponseEntity<List<DealDtos.OportunidadeResponse>> listarOportunidades(
            @PathVariable UUID funilId) {

        UUID responsavelId = filtroDeResponsavel(Permissao.DEALS_READ);

        // Confirma que o funil é do tenant antes de listar. O RLS já barraria,
        // mas devolver lista vazia para um id de outro cliente é pior que
        // devolver "não encontrado": esconde o erro de quem está integrando.
        funis.findByIdAndTenantIdAndDeletedAtIsNull(funilId, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funil"));

        List<DealEntity> negocios = oportunidades.listarDoFunil(
                TenantContext.obrigatorio(), funilId, responsavelId);
        Map<UUID, UsuarioLookup.UsuarioReference> responsaveis = users.findKnown(
                TenantContext.obrigatorio(), negocios.stream()
                        .map(DealEntity::getOwnerUserId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList());

        return ResponseEntity.ok(negocios.stream()
                .map(negocio -> paraResposta(
                        negocio, responsaveis.get(negocio.getOwnerUserId())))
                .toList());
    }

    /** Ver a explicação equivalente em {@code ContactController}. */
    private UUID filtroDeResponsavel(Permissao permissao) {
        return autorizacao.alcanceDe(permissao) == Autorizacao.Alcance.PROPRIO
                ? autorizacao.usuarioCorrente()
                : null;
    }

    @PostMapping("/oportunidades")
    @Transactional
    ResponseEntity<DealDtos.OportunidadeResponse> criar(
            @Valid @RequestBody DealDtos.OportunidadeRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        autorizacao.exigir(Permissao.DEALS_WRITE);

        UUID autorId = UUID.fromString(jwt.getSubject());
        PipelineStageEntity etapa = carregarEtapa(requisicao.etapaId());

        DealEntity oportunidade = DealEntity.nova(
                TenantContext.obrigatorio(), etapa.getPipelineId(), etapa.getId(), autorId);
        aplicar(oportunidade, requisicao, autorId);
        // Passa pelo mesmo caminho de mudança de etapa, para que uma
        // oportunidade criada já em "Ganho" nasça com o status correto.
        oportunidade.moverPara(etapa, autorId);
        autorizacao.exigirSobreRegistro(Permissao.DEALS_WRITE, oportunidade.getOwnerUserId());

        return ResponseEntity.ok(paraResposta(oportunidades.save(oportunidade)));
    }

    @PutMapping("/oportunidades/{id}")
    @Transactional
    ResponseEntity<DealDtos.OportunidadeResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody DealDtos.OportunidadeRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        autorizacao.exigir(Permissao.DEALS_WRITE);
        UUID autorId = UUID.fromString(jwt.getSubject());
        DealEntity oportunidade = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.DEALS_WRITE, oportunidade.getOwnerUserId());
        aplicar(oportunidade, requisicao, autorId);
        oportunidade.moverPara(carregarEtapa(requisicao.etapaId()), autorId);
        autorizacao.exigirSobreRegistro(Permissao.DEALS_WRITE, oportunidade.getOwnerUserId());

        return ResponseEntity.ok(paraResposta(oportunidade));
    }

    /** Operação do arrastar-e-soltar do kanban. */
    @PostMapping("/oportunidades/{id}/mover")
    @Transactional
    ResponseEntity<DealDtos.OportunidadeResponse> mover(
            @PathVariable UUID id,
            @Valid @RequestBody DealDtos.MoverRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        autorizacao.exigir(Permissao.DEALS_WRITE);
        UUID autorId = UUID.fromString(jwt.getSubject());
        DealEntity oportunidade = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.DEALS_WRITE, oportunidade.getOwnerUserId());
        oportunidade.moverPara(carregarEtapa(requisicao.etapaId()), autorId);
        oportunidade.registrarMotivoDePerda(requisicao.motivoPerda());

        return ResponseEntity.ok(paraResposta(oportunidade));
    }

    @DeleteMapping("/oportunidades/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigir(Permissao.DEALS_WRITE);
        DealEntity oportunidade = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.DEALS_WRITE, oportunidade.getOwnerUserId());
        oportunidade.excluir(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    private List<DealDtos.EtapaResponse> etapasDe(UUID funilId) {
        return etapas.findByTenantIdAndPipelineIdAndDeletedAtIsNullOrderByPosition(
                        TenantContext.obrigatorio(), funilId).stream()
                .map(e -> new DealDtos.EtapaResponse(
                        e.getId(), e.getName(), e.getPosition(), e.isWon(), e.isLost()))
                .toList();
    }

    private DealEntity carregar(UUID id) {
        return oportunidades.findByIdAndTenantIdAndDeletedAtIsNull(id, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Oportunidade"));
    }

    private PipelineStageEntity carregarEtapa(UUID etapaId) {
        return etapas.findByIdAndTenantIdAndDeletedAtIsNull(etapaId, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etapa"));
    }

    private void aplicar(DealEntity d, DealDtos.OportunidadeRequest r, UUID autorId) {
        UUID tenantId = TenantContext.obrigatorio();
        UUID responsavelId = autorizacao.responsavelPadrao(
                Permissao.DEALS_WRITE, r.responsavelId());
        autorizacao.exigirSobreRegistro(Permissao.DEALS_WRITE, responsavelId);
        if (responsavelId != null && !users.existsActive(tenantId, responsavelId)) {
            throw new RecursoNaoEncontradoException("Responsável");
        }
        if (r.contatoId() != null) {
            ContactLookup.ContactReference contato = contacts.findReference(tenantId, r.contatoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Contato"));
            autorizacao.exigirSobreRegistro(
                    Permissao.CONTACTS_READ, contato.ownerUserId());
        }
        d.aplicar(r.titulo(), r.valorCentavos(), r.contatoId(),
                r.previsaoFechamento(), responsavelId,
                autorId);
    }

    private DealDtos.OportunidadeResponse paraResposta(DealEntity d) {
        UsuarioLookup.UsuarioReference responsavel = d.getOwnerUserId() == null
                ? null
                : users.findKnown(TenantContext.obrigatorio(), List.of(d.getOwnerUserId()))
                        .get(d.getOwnerUserId());
        return paraResposta(d, responsavel);
    }

    private static DealDtos.OportunidadeResponse paraResposta(
            DealEntity d, UsuarioLookup.UsuarioReference responsavel) {
        return new DealDtos.OportunidadeResponse(
                d.getId(), d.getPipelineId(), d.getStageId(), d.getContactId(), d.getTitle(),
                d.getValueCents(), d.getStatus().name(), d.getExpectedCloseDate(),
                d.getLostReason(), d.getOwnerUserId(),
                responsavel == null ? null : responsavel.login(),
                responsavel == null ? null : responsavel.nomeCompleto(),
                d.getCreatedAt());
    }
}
