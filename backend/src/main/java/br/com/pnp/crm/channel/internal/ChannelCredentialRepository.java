package br.com.pnp.crm.channel.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ChannelCredentialRepository extends JpaRepository<ChannelCredentialEntity, UUID> {

    Optional<ChannelCredentialEntity> findByChannelConnectionIdAndKindAndDeletedAtIsNull(
            UUID channelConnectionId, TipoCredencial kind);
}
