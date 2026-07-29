package br.com.pnp.crm.channel.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ChannelConnectionRepository extends JpaRepository<ChannelConnectionEntity, UUID> {

    Optional<ChannelConnectionEntity> findByIdAndActiveTrueAndDeletedAtIsNull(UUID id);
}
