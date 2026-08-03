package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import br.com.pnp.crm.tenant.api.DefaultPipelineTemplate;
import br.com.pnp.crm.tenant.api.TenantPresentationLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cria uma única vez o funil inicial definido pelo preset do tenant. */
@Service
class FunilPadraoService {

    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final TenantPresentationLookup presentations;
    private final Autorizacao autorizacao;

    FunilPadraoService(PipelineRepository pipelines, PipelineStageRepository stages,
                       TenantPresentationLookup presentations, Autorizacao autorizacao) {
        this.pipelines = pipelines;
        this.stages = stages;
        this.presentations = presentations;
        this.autorizacao = autorizacao;
    }

    /**
     * O INSERT usa ON CONFLICT contra o índice parcial da V5. Duas requisições
     * podem chegar juntas: somente uma insere e cria etapas; a outra espera a
     * decisão da constraint e relê o funil vencedor.
     */
    @Transactional
    PipelineEntity obterOuCriar(UUID authorId) {
        UUID tenantId = TenantContext.obrigatorio();
        return pipelines.findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
                .orElseGet(() -> createIfAbsent(tenantId, authorId));
    }

    private PipelineEntity createIfAbsent(UUID tenantId, UUID authorId) {
        // A primeira leitura materializa o funil do preset. Embora seja
        // idempotente, continua sendo escrita: um perfil somente-leitura não
        // pode criar estrutura e aparecer como autor dela. A decisão fica no
        // ramo que escreve para não abrir janela entre "não existe" e criar.
        autorizacao.exigir(Permissao.DEALS_WRITE);
        var presentation = presentations.atual();
        if (!presentation.onboardingCompleted()) {
            // Uma chamada direta à API não pode contornar o onboarding e criar
            // o funil geral antes da escolha real do segmento.
            throw new PerfilInicialPendenteException();
        }
        DefaultPipelineTemplate template = presentation.defaultPipeline();
        UUID pipelineId = UuidV7.gerar();
        int inserted = pipelines.insertDefaultIfAbsent(
                pipelineId, tenantId, template.name(), authorId);

        if (inserted == 1) {
            template.stages().forEach(stage -> stages.save(PipelineStageEntity.nova(
                    tenantId, pipelineId, stage.name(), stage.position(),
                    stage.won(), stage.lost(), authorId)));
            return pipelines.findByIdAndTenantIdAndDeletedAtIsNull(pipelineId, tenantId)
                    .orElseThrow();
        }

        return pipelines.findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "A constraint informou um funil padrão, mas ele não pôde ser relido."));
    }
}
