package br.com.pnp.crm.channel.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de consulta de conexões, para outros módulos.
 */
public interface ChannelConnectionLookup {

    /**
     * Descobre o tenant de uma conexão <b>antes</b> de existir contexto de
     * tenant. É o que torna possível processar uma mensagem que chega por
     * webhook, onde não há sessão.
     *
     * <p>Resolve roteamento, não autorização: quem garante que a requisição é
     * legítima é a assinatura do provedor, verificada pelo adaptador antes de
     * chamar isto.
     */
    Optional<UUID> resolverTenantId(UUID channelConnectionId);

    /**
     * Busca uma conexão ativa dentro do tenant corrente. Exige contexto de
     * tenant já definido — diferente de {@link #resolverTenantId}, esta passa
     * pelo RLS normalmente.
     */
    Optional<ConexaoDeCanal> buscarAtiva(UUID channelConnectionId);
}
