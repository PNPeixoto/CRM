package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.ValidadorSessaoTempoReal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/** O WebSocket tem corte imediato quando logout/revogacao mata a familia. */
@Service
class ValidadorSessaoTempoRealImpl implements ValidadorSessaoTempoReal {

    private final RefreshTokenRepository tokens;
    private final TransactionTemplate transacoes;

    ValidadorSessaoTempoRealImpl(RefreshTokenRepository tokens,
                                 PlatformTransactionManager transactionManager) {
        this.tokens = tokens;
        this.transacoes = new TransactionTemplate(transactionManager);
        this.transacoes.setReadOnly(true);
    }

    @Override
    public void exigirAtiva(UUID tenantId, UUID usuarioId, UUID sessaoId) {
        boolean ativa = TenantContext.executarComo(tenantId, () -> {
            Boolean resultado = transacoes.execute(ignored -> {
                Instant agora = Instant.now();
                return tokens.existeSessaoAtiva(
                        tenantId, usuarioId, sessaoId, agora,
                        agora.minus(RefreshTokenService.INATIVIDADE_MAXIMA),
                        agora.minus(RefreshTokenService.VALIDADE_ABSOLUTA));
            });
            return Boolean.TRUE.equals(resultado);
        });
        if (!ativa) throw new AcessoNegadoException();
    }
}
