package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.ChannelConnectionLookup;
import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Resolve o tenant da mensagem e delega a escrita para a fronteira transacional. */
@Service
class IngestaoDeMensagemService {

    private static final Logger log = LoggerFactory.getLogger(IngestaoDeMensagemService.class);

    private final ChannelConnectionLookup connections;
    private final PersistenciaDeMensagemRecebida persistence;

    IngestaoDeMensagemService(ChannelConnectionLookup connections,
                              PersistenciaDeMensagemRecebida persistence) {
        this.connections = connections;
        this.persistence = persistence;
    }

    Optional<UUID> registrar(InboundMessage input) {
        UUID tenantId = connections.resolverTenantId(input.channelConnectionId()).orElse(null);
        if (tenantId == null) {
            log.warn("Mensagem recebida para conexão desconhecida ou inativa. connectionId={}",
                    input.channelConnectionId());
            return Optional.empty();
        }

        // persistence é outro bean: a chamada atravessa o proxy e @Transactional
        // abre uma transação real. Auto-invocação nesta classe não faria isso.
        return TenantContext.executarComo(
                tenantId, () -> persistence.persist(tenantId, input));
    }
}
