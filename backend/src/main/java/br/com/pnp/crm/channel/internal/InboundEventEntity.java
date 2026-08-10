package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbound_event")
class InboundEventEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "channel_connection_id", nullable = false)
    private UUID channelConnectionId;

    @Column(name = "external_event_id", nullable = false)
    private String externalEventId;

    // Guardado como texto cru convertido para jsonb pelo banco. Não passa por
    // desserialização e reserialização: o valor precisa ser o que chegou.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "payload_ciphertext")
    private String payloadCiphertext;

    @Column(name = "payload_sha256", length = 64)
    private String payloadSha256;

    @Column(name = "payload_retain_until", nullable = false, insertable = false, updatable = false)
    private Instant payloadRetainUntil;

    @Column(name = "payload_purged_at")
    private Instant payloadPurgedAt;

    @Column(name = "lease_owner")
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason")
    private String failureReason;

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

    protected InboundEventEntity() {
        // exigido pelo JPA
    }

    static InboundEventEntity novo(UUID tenantId, UUID channelConnectionId,
                                   String externalEventId, String payloadCiphertext,
                                   String payloadSha256) {
        InboundEventEntity entity = new InboundEventEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.channelConnectionId = channelConnectionId;
        entity.externalEventId = externalEventId;
        entity.payloadCiphertext = payloadCiphertext;
        entity.payloadSha256 = payloadSha256;
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getChannelConnectionId() {
        return channelConnectionId;
    }

    String payloadParaProcessamento(CofreDeCredenciais cofre) {
        if (payloadCiphertext != null) {
            return cofre.decifrar(payloadCiphertext);
        }
        if (payload != null) {
            return payload;
        }
        throw new IllegalStateException("Payload do evento ja foi expurgado.");
    }

    void marcarProcessado() {
        this.processedAt = Instant.now();
        this.failureReason = null;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.deadLetteredAt = null;
    }

    void marcarFalha(String motivo) {
        this.failureReason = motivo;
    }
}
