package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.ChannelConnectionLookup;
import br.com.pnp.crm.channel.api.ConexaoDeCanal;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class ChannelConnectionLookupImpl implements ChannelConnectionLookup {

    private final ChannelConnectionRepository repository;
    private final EntityManager entityManager;

    ChannelConnectionLookupImpl(ChannelConnectionRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    /**
     * Atravessa o RLS pela função {@code SECURITY DEFINER} da V2, porque neste
     * ponto ainda não há tenant no contexto — a mensagem chegou por webhook ou
     * por widget, sem sessão.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> resolverTenantId(UUID channelConnectionId) {
        if (channelConnectionId == null) {
            return Optional.empty();
        }
        return entityManager
                .createNativeQuery("SELECT resolve_tenant_id_por_channel_connection(:id)", UUID.class)
                .setParameter("id", channelConnectionId)
                .getResultStream()
                .findFirst()
                .map(UUID.class::cast);
    }

    /**
     * Consulta normal, sujeita ao RLS. Exige tenant já definido no contexto —
     * sem ele devolve vazio, que é o comportamento correto e não um bug.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ConexaoDeCanal> buscarAtiva(UUID channelConnectionId) {
        if (channelConnectionId == null) {
            return Optional.empty();
        }
        return repository.findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(
                        channelConnectionId, TenantContext.obrigatorio())
                .map(entity -> new ConexaoDeCanal(
                        entity.getId(), entity.getTenantId(), entity.getKind(), entity.getName(),
                        entity.getExternalAccountId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ConexaoDeCanal> buscarConhecidas(Collection<UUID> channelConnectionIds) {
        if (channelConnectionIds == null || channelConnectionIds.isEmpty()) return Map.of();
        return repository.findByTenantIdAndIdInAndDeletedAtIsNull(
                        TenantContext.obrigatorio(), channelConnectionIds).stream()
                .map(entity -> new ConexaoDeCanal(
                        entity.getId(), entity.getTenantId(), entity.getKind(), entity.getName(),
                        entity.getExternalAccountId()))
                .collect(Collectors.toUnmodifiableMap(ConexaoDeCanal::id, Function.identity()));
    }
}
