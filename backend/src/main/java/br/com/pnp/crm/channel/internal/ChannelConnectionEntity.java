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

    static ChannelConnectionEntity nova(UUID tenantId, TipoCanal tipo, String nome,
                                        String identificadorExterno, UUID autorId) {
        ChannelConnectionEntity entity = new ChannelConnectionEntity();
        entity.id = br.com.pnp.crm.shared.api.UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.kind = tipo;
        entity.name = nome;
        entity.externalAccountId = normalizar(identificadorExterno);
        // Nasce ativa: um canal recém-criado que não recebe mensagem sem um
        // segundo clique é uma fonte previsível de "não funciona".
        entity.active = true;
        entity.createdBy = autorId;
        entity.updatedBy = autorId;
        return entity;
    }

    void renomear(String nome, String identificadorExterno, UUID autorId) {
        this.name = nome;
        this.externalAccountId = normalizar(identificadorExterno);
        this.updatedBy = autorId;
    }

    /**
     * Desativar em vez de excluir. Conversas e mensagens referenciam a conexão;
     * removê-la deixaria o histórico órfão, e o histórico é o produto.
     */
    void alternarAtivacao(UUID autorId) {
        this.active = !this.active;
        this.updatedBy = autorId;
    }

    String getExternalAccountId() {
        return externalAccountId;
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
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
