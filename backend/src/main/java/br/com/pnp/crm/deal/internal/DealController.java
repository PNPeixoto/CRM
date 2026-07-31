package br.com.pnp.crm.deal.internal;

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
import java.util.UUID;

@RestController
@RequestMapping("/api")
class DealController {

    private final PipelineRepository funis;
    private final PipelineStageRepository etapas;
    private final DealRepository oportunidades;
    private final FunilPadraoService funilPadrao;

    DealController(PipelineRepository funis, PipelineStageRepository etapas,
                   DealRepository oportunidades, FunilPadraoService funilPadrao) {
        this.funis = funis;
        this.etapas = etapas;
        this.oportunidades = oportunidades;
        this.funilPadrao = funilPadrao;
    }

    /**
     * Devolve os funis, criando o padrão na primeira chamada. É por isso que
     * este endpoint não é somente-leitura: sem ele, a tela do kanban abriria
     * vazia e sem colunas no primeiro acesso.
     */
    @GetMapping("/funis")
    @Transactional
    ResponseEntity<List<DealDtos.FunilResponse>> listarFunis(@AuthenticationPrincipal Jwt jwt) {
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

        // Confirma que o funil é do tenant antes de listar. O RLS já barraria,
        // mas devolver lista vazia para um id de outro cliente é pior que
        // devolver "não encontrado": esconde o erro de quem está integrando.
        funis.findByIdAndTenantIdAndDeletedAtIsNull(funilId, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funil"));

        return ResponseEntity.ok(
                oportunidades.findByTenantIdAndPipelineIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                                TenantContext.obrigatorio(), funilId)
                        .stream().map(DealController::paraResposta).toList());
    }

    @PostMapping("/oportunidades")
    @Transactional
    ResponseEntity<DealDtos.OportunidadeResponse> criar(
            @Valid @RequestBody DealDtos.OportunidadeRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        UUID autorId = UUID.fromString(jwt.getSubject());
        PipelineStageEntity etapa = carregarEtapa(requisicao.etapaId());

        DealEntity oportunidade = DealEntity.nova(
                TenantContext.obrigatorio(), etapa.getPipelineId(), etapa.getId(), autorId);
        aplicar(oportunidade, requisicao, autorId);
        // Passa pelo mesmo caminho de mudança de etapa, para que uma
        // oportunidade criada já em "Ganho" nasça com o status correto.
        oportunidade.moverPara(etapa, autorId);

        return ResponseEntity.ok(paraResposta(oportunidades.save(oportunidade)));
    }

    @PutMapping("/oportunidades/{id}")
    @Transactional
    ResponseEntity<DealDtos.OportunidadeResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody DealDtos.OportunidadeRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        UUID autorId = UUID.fromString(jwt.getSubject());
        DealEntity oportunidade = carregar(id);
        aplicar(oportunidade, requisicao, autorId);
        oportunidade.moverPara(carregarEtapa(requisicao.etapaId()), autorId);

        return ResponseEntity.ok(paraResposta(oportunidade));
    }

    /** Operação do arrastar-e-soltar do kanban. */
    @PostMapping("/oportunidades/{id}/mover")
    @Transactional
    ResponseEntity<DealDtos.OportunidadeResponse> mover(
            @PathVariable UUID id,
            @Valid @RequestBody DealDtos.MoverRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        UUID autorId = UUID.fromString(jwt.getSubject());
        DealEntity oportunidade = carregar(id);
        oportunidade.moverPara(carregarEtapa(requisicao.etapaId()), autorId);
        oportunidade.registrarMotivoDePerda(requisicao.motivoPerda());

        return ResponseEntity.ok(paraResposta(oportunidade));
    }

    @DeleteMapping("/oportunidades/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        carregar(id).excluir(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    private List<DealDtos.EtapaResponse> etapasDe(UUID funilId) {
        return etapas.findByPipelineIdAndDeletedAtIsNullOrderByPosition(funilId).stream()
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
        d.aplicar(r.titulo(), r.valorCentavos(), r.contatoId(),
                r.previsaoFechamento(), r.responsavelId(), autorId);
    }

    private static DealDtos.OportunidadeResponse paraResposta(DealEntity d) {
        return new DealDtos.OportunidadeResponse(
                d.getId(), d.getPipelineId(), d.getStageId(), d.getContactId(), d.getTitle(),
                d.getValueCents(), d.getStatus().name(), d.getExpectedCloseDate(),
                d.getLostReason(), d.getOwnerUserId(), d.getCreatedAt());
    }
}
