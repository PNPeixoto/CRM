package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cria o funil inicial do tenant na primeira vez que ele abre a tela.
 *
 * <p>Um CRM que abre com o kanban vazio e sem colunas não é utilizável: o
 * usuário precisaria configurar um funil antes de entender para que serve.
 * As etapas abaixo são o funil de vendas comum, e existem para serem
 * renomeadas — não para serem definitivas.
 */
@Service
class FunilPadraoService {

    private static final String NOME_PADRAO = "Funil de vendas";

    /**
     * Posições esparsas de 10 em 10, para permitir inserir uma etapa entre
     * duas sem renumerar todas as outras.
     */
    private static final List<EtapaInicial> ETAPAS = List.of(
            new EtapaInicial("Novo lead", 10, false, false),
            new EtapaInicial("Contato feito", 20, false, false),
            new EtapaInicial("Proposta enviada", 30, false, false),
            new EtapaInicial("Negociação", 40, false, false),
            new EtapaInicial("Ganho", 50, true, false),
            new EtapaInicial("Perdido", 60, false, true));

    private final PipelineRepository funis;
    private final PipelineStageRepository etapas;

    FunilPadraoService(PipelineRepository funis, PipelineStageRepository etapas) {
        this.funis = funis;
        this.etapas = etapas;
    }

    /**
     * Devolve o funil padrão, criando-o se ainda não existir.
     *
     * <p>O índice único parcial da V5 garante um só padrão por tenant. Se duas
     * requisições simultâneas tentarem criar, a segunda falha na constraint e
     * relê — mesmo tratamento de corrida usado na ingestão de mensagem.
     */
    @Transactional
    PipelineEntity obterOuCriar(UUID autorId) {
        UUID tenantId = TenantContext.obrigatorio();

        return funis.findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId)
                .orElseGet(() -> criar(tenantId, autorId));
    }

    private PipelineEntity criar(UUID tenantId, UUID autorId) {
        PipelineEntity funil = funis.saveAndFlush(
                PipelineEntity.novo(tenantId, NOME_PADRAO, true, autorId));

        ETAPAS.forEach(etapa -> etapas.save(PipelineStageEntity.nova(
                tenantId, funil.getId(), etapa.nome(), etapa.posicao(),
                etapa.ganho(), etapa.perda(), autorId)));

        return funil;
    }

    private record EtapaInicial(String nome, int posicao, boolean ganho, boolean perda) {
    }
}
