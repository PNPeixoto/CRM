package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.AutorizacaoDeEscuta;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementa a porta de escuta consultando o mesmo membership do HTTP.
 *
 * <p>Não há cache: uma inscrição por sessão é rara o bastante para pagar uma
 * consulta, e cachear aqui reintroduziria exatamente a janela que este ponto
 * existe para fechar.
 */
@Service
class AutorizacaoDeEscutaService implements AutorizacaoDeEscuta {

    private static final Logger log = LoggerFactory.getLogger(AutorizacaoDeEscutaService.class);

    private final OrganizationAccess acessos;

    AutorizacaoDeEscutaService(OrganizationAccess acessos) {
        this.acessos = acessos;
    }

    @Override
    public void exigirEscutaDeConversas(UUID tenantId, UUID usuarioId) {
        // O tenant vem do token já verificado pelo interceptador, e é aplicado
        // no banco para que a consulta de membership passe pelo mesmo RLS do
        // caminho HTTP.
        OrganizationAccess.ScopeType escopo = TenantContext.executarComo(tenantId,
                () -> acessos.permissionScopes(tenantId, usuarioId)
                        .get(Permissao.CONVERSATIONS_READ.codigo()));

        // Os tópicos atuais são compartilhados pelo tenant. OWN não pode ser
        // promovido silenciosamente a TENANT só porque o transporte não sabe
        // filtrar mensagens por responsável. Quando houver tópico individual,
        // ele receberá uma decisão própria e uma publicação correspondente.
        if (escopo != OrganizationAccess.ScopeType.TENANT) {
            log.warn("SUBSCRIBE recusado por autorização. usuario={}", usuarioId);
            throw new AcessoNegadoException();
        }
    }
}
