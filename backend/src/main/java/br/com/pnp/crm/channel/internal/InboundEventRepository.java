package br.com.pnp.crm.channel.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface InboundEventRepository extends JpaRepository<InboundEventEntity, UUID> {
}
