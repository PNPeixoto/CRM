package br.com.pnp.crm.privacy.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aplica a retencao configurada, categoria por categoria e tenant por tenant.
 *
 * <p><b>Nasce desligado.</b> A anotacao exige
 * {@code app.privacidade.retencao.habilitada=true}, e o padrao e {@code false}.
 * Um worker de expurgo que sobe ligado por omissao apaga dado de cliente na
 * primeira vez que alguem esquece de configurar — e apagar e irreversivel.
 *
 * <p><b>Prazo zero desliga a categoria.</b> Nao existe fallback: se ninguem
 * decidiu quantos dias uma categoria retem, ela nao e tocada. Um default
 * embutido seria uma decisao juridica tomada por quem escreveu o codigo.
 *
 * <p>O corte e calculado aqui e passado as funcoes do banco, que nunca chamam
 * {@code now()}. E o que torna a retencao verificavel com relogio controlado.
 */
@Component
@ConditionalOnProperty(name = "app.privacidade.retencao.habilitada", havingValue = "true")
class RetencaoPorCategoriaWorker {

    private static final Logger log = LoggerFactory.getLogger(RetencaoPorCategoriaWorker.class);

    /** Categoria, funcao no banco e propriedade de prazo. */
    private record Categoria(String nome, String funcao, int dias) {
    }

    private final EntityManager entityManager;
    private final TransactionTemplate transacoes;
    private final Clock relogio;
    private final int lote;
    private final List<Categoria> categorias;

    // O projeto nao expoe um bean de Clock; cada componente que precisa de
    // tempo controlavel recebe o relogio por construtor, como
    // `OrganizationAccessService` ja faz.
    RetencaoPorCategoriaWorker(
            EntityManager entityManager, PlatformTransactionManager transactionManager,
            @Value("${app.privacidade.retencao.tamanho-do-lote:200}") int lote,
            @Value("${app.privacidade.retencao.dias.conversas-encerradas:0}") int conversas,
            @Value("${app.privacidade.retencao.dias.contatos-excluidos:0}") int contatos,
            @Value("${app.privacidade.retencao.dias.telemetria:0}") int telemetria,
            @Value("${app.privacidade.retencao.dias.eventos-tempo-real:0}") int tempoReal,
            @Value("${app.privacidade.retencao.dias.tentativas-de-conector:0}") int conector,
            @Value("${app.privacidade.retencao.dias.auditoria:0}") int auditoria) {
        this(entityManager, transactionManager, Clock.systemUTC(), lote,
                conversas, contatos, telemetria, tempoReal, conector, auditoria);
    }

    /** Relógio explícito: é o que permite provar a retenção sem esperar dias. */
    RetencaoPorCategoriaWorker(
            EntityManager entityManager, PlatformTransactionManager transactionManager,
            Clock relogio, int lote, int conversas, int contatos, int telemetria,
            int tempoReal, int conector, int auditoria) {
        this.entityManager = entityManager;
        this.transacoes = new TransactionTemplate(transactionManager);
        this.relogio = relogio;
        this.lote = lote;
        this.categorias = List.of(
                new Categoria("conversas", "expurgar_conversas_encerradas", conversas),
                new Categoria("contatos", "anonimizar_contatos", contatos),
                new Categoria("telemetria", "expurgar_telemetria", telemetria),
                new Categoria("tempo-real", "expurgar_eventos_tempo_real", tempoReal),
                new Categoria("conector", "expurgar_tentativas_de_conector", conector),
                new Categoria("auditoria", "expurgar_auditoria", auditoria));
    }

    @Scheduled(fixedDelayString = "${app.privacidade.retencao.intervalo-ms:3600000}")
    void aplicar() {
        Instant agora = relogio.instant();
        for (UUID tenantId : tenants()) {
            for (Categoria categoria : categorias) {
                if (categoria.dias() <= 0) {
                    continue;
                }
                executar(tenantId, categoria, agora.minus(Duration.ofDays(categoria.dias())));
            }
        }
    }

    private void executar(UUID tenantId, Categoria categoria, Instant corte) {
        try {
            Integer tratados = TenantContext.executarComo(tenantId, () ->
                    transacoes.execute(ignored -> (Integer) entityManager
                            .createNativeQuery("SELECT " + categoria.funcao() + "(:corte, :lote)")
                            .setParameter("corte", corte)
                            .setParameter("lote", lote)
                            .getSingleResult()));
            if (tratados != null && tratados > 0) {
                // Conta, categoria e tenant. Nunca o que foi apagado: um log de
                // expurgo que listasse os registros recriaria o dado que o
                // expurgo existe para remover.
                log.info("Retencao aplicada. categoria={} tenant={} tratados={}",
                        categoria.nome(), tenantId, tratados);
            }
        } catch (RuntimeException e) {
            // Uma categoria que falha nao pode impedir as demais, e a proxima
            // passada tenta de novo — o expurgo e idempotente por desenho.
            log.warn("Falha sanitizada na retencao. categoria={} tenant={}",
                    categoria.nome(), tenantId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<UUID> tenants() {
        return transacoes.execute(ignored -> entityManager
                .createNativeQuery("SELECT id FROM tenant WHERE deleted_at IS NULL")
                .getResultList());
    }
}
