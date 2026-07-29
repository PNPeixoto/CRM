package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.conversation.api.StatusConversa;
import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation")
class ConversationEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // UUID solto, sem @ManyToOne para ChannelConnectionEntity. A fronteira do
    // Spring Modulith proíbe referenciar entidade de outro módulo; a
    // integridade fica com a chave estrangeira no banco.
    @Column(name = "channel_connection_id", nullable = false)
    private UUID channelConnectionId;

    @Column(name = "external_contact_id", nullable = false)
    private String externalContactId;

    @Column(name = "contact_display_name")
    private String contactDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConversa status;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

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

    protected ConversationEntity() {
        // exigido pelo JPA
    }

    static ConversationEntity abrir(UUID tenantId, UUID channelConnectionId,
                                    String externalContactId, String contactDisplayName) {
        ConversationEntity entity = new ConversationEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.channelConnectionId = channelConnectionId;
        entity.externalContactId = externalContactId;
        entity.contactDisplayName = contactDisplayName;
        entity.status = StatusConversa.OPEN;
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

    String getExternalContactId() {
        return externalContactId;
    }

    String getContactDisplayName() {
        return contactDisplayName;
    }

    StatusConversa getStatus() {
        return status;
    }

    UUID getAssignedUserId() {
        return assignedUserId;
    }

    Instant getLastMessageAt() {
        return lastMessageAt;
    }

    /**
     * O nome vindo do provedor pode mudar a qualquer momento — o interlocutor
     * troca o nome de exibição no Telegram, por exemplo. Sobrescrever com
     * valor vazio apagaria o que já sabíamos, então só atualiza quando há
     * conteúdo.
     */
    void atualizarNomeExibicao(String nome) {
        if (nome != null && !nome.isBlank()) {
            this.contactDisplayName = nome;
        }
    }

    /**
     * Só avança. Webhooks chegam fora de ordem com frequência, e deixar um
     * evento atrasado recuar o carimbo bagunçaria a ordenação da caixa de
     * entrada — a conversa "pularia" para baixo ao receber uma mensagem antiga.
     */
    void registrarAtividade(Instant momento) {
        if (lastMessageAt == null || momento.isAfter(lastMessageAt)) {
            this.lastMessageAt = momento;
        }
    }

    /**
     * Mensagem nova reabre conversa encerrada? Não: o índice único parcial da
     * V2 impede duas conversas não encerradas para o mesmo interlocutor, e
     * reabrir aqui criaria conflito com uma conversa nova criada em paralelo.
     * A reabertura é decisão de atendimento, não efeito colateral de ingestão.
     */
    void reabrirParaAtendimento() {
        if (status == StatusConversa.PENDING) {
            this.status = StatusConversa.OPEN;
        }
    }
}
