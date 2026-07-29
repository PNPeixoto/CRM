package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.TipoCanal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channel_connection")
class ChannelConnectionEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // STRING e não ORDINAL: com ordinal, inserir um valor novo no meio do enum
    // reinterpreta silenciosamente todas as linhas já gravadas. E o CHECK da
    // coluna trabalha sobre o texto.
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private TipoCanal kind;

    @Column(nullable = false)
    private String name;

    @Column(name = "external_account_id")
    private String externalAccountId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ChannelConnectionEntity() {
        // exigido pelo JPA
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    TipoCanal getKind() {
        return kind;
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return active;
    }
}
