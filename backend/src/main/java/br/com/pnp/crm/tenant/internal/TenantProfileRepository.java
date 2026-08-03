package br.com.pnp.crm.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TenantProfileRepository extends JpaRepository<TenantProfileEntity, UUID> {
}
