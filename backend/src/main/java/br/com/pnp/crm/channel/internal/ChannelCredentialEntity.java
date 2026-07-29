package br.com.pnp.crm.channel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channel_credential")
class ChannelCredentialEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "channel_connection_id", nullable = false)
    private UUID channelConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCredencial kind;

    @Column(nullable = false)
    private String ciphertext;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

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

    protected ChannelCredentialEntity() {
        // exigido pelo JPA
    }

    static ChannelCredentialEntity nova(UUID tenantId, UUID channelConnectionId,
                                        TipoCredencial kind, String ciphertext) {
        ChannelCredentialEntity entity = new ChannelCredentialEntity();
        entity.id = br.com.pnp.crm.shared.api.UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.channelConnectionId = channelConnectionId;
        entity.kind = kind;
        entity.ciphertext = ciphertext;
        entity.keyVersion = CofreDeCredenciais.VERSAO_CHAVE_ATUAL;
        return entity;
    }

    String getCiphertext() {
        return ciphertext;
    }

    /**
     * Sem {@code toString()} gerado e sem getter público do texto cifrado além
     * do necessário. Entidade com credencial que caia em um log — por um
     * {@code log.debug("{}", entidade)} distraído — vaza material sensível
     * mesmo cifrado, e cifrado hoje é decifrável amanhã se a chave vazar.
     */
    @Override
    public String toString() {
        return "ChannelCredentialEntity[id=" + id + ", kind=" + kind + "]";
    }
}
