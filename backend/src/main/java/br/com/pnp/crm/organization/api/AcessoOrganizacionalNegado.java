package br.com.pnp.crm.organization.api;

import java.util.Objects;
import java.util.UUID;

/** Evento publico e sanitizado; o modulo de auditoria decide como persisti-lo. */
public record AcessoOrganizacionalNegado(UUID tenantId, UUID actorId, Motivo motivo) {

    public AcessoOrganizacionalNegado {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(motivo);
    }

    public enum Motivo {
        PERMISSION_MISSING,
        MEMBERSHIP_INVALID,
        SCOPE_DENIED
    }
}
