package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.conversation.api.ConversationMetrics;
import br.com.pnp.crm.conversation.api.StatusConversa;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
class ConversationMetricsImpl implements ConversationMetrics {

    /**
     * "Hoje" é o dia em São Paulo, não em UTC.
     *
     * <p>O banco guarda em UTC, mas o atendente que abre o dashboard às 21h de
     * Brasília já estaria no dia seguinte em UTC — e veria a contagem zerar no
     * meio do expediente. O fuso de negócio é o de quem lê.
     */
    private static final ZoneId FUSO_DE_NEGOCIO = ZoneId.of("America/Sao_Paulo");

    private final ConversationRepository conversas;
    private final MessageRepository mensagens;

    ConversationMetricsImpl(ConversationRepository conversas, MessageRepository mensagens) {
        this.conversas = conversas;
        this.mensagens = mensagens;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumoDeConversas resumo() {
        UUID tenantId = TenantContext.obrigatorio();
        Instant inicioDoDia = LocalDate.now(FUSO_DE_NEGOCIO)
                .atStartOfDay(FUSO_DE_NEGOCIO).toInstant();

        return new ResumoDeConversas(
                conversas.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, StatusConversa.OPEN),
                conversas.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, StatusConversa.PENDING),
                mensagens.contarRecebidasDesde(tenantId, inicioDoDia));
    }
}
