package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.conversation.api.DirecaoMensagem;
import br.com.pnp.crm.conversation.api.StatusMensagem;
import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "message")
class MessageEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "channel_connection_id", nullable = false)
    private UUID channelConnectionId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DirecaoMensagem direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private TipoConteudo contentType;

    @Column(name = "text_content")
    private String textContent;

    // Mapeia para jsonb do Postgres. O conteúdo é do provedor e existe só para
    // diagnóstico — nenhuma regra de negócio lê daqui, senão a normalização
    // deixa de valer e o domínio volta a conhecer o provedor.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMensagem status;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "lease_owner")
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

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

    protected MessageEntity() {
        // exigido pelo JPA
    }

    static MessageEntity recebida(UUID tenantId, UUID conversationId, UUID channelConnectionId,
                                  String externalId, TipoConteudo contentType, String texto,
                                  Instant ocorridoEm, Map<String, Object> payload) {
        MessageEntity entity = new MessageEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.conversationId = conversationId;
        entity.channelConnectionId = channelConnectionId;
        entity.externalId = externalId;
        entity.direction = DirecaoMensagem.INBOUND;
        entity.contentType = contentType;
        entity.textContent = texto;
        entity.status = StatusMensagem.RECEIVED;
        // sent_at recebe o instante informado pelo PROVEDOR, não o nosso now():
        // a diferença entre os dois é a latência da entrega, e ela some se
        // sobrescrevermos com a hora local.
        entity.sentAt = ocorridoEm;
        entity.payload = payload;
        return entity;
    }

    static MessageEntity paraEnvio(UUID tenantId, UUID conversationId, UUID channelConnectionId,
                                   TipoConteudo contentType, String texto, UUID autorId,
                                   String idempotencyKey) {
        MessageEntity entity = new MessageEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.conversationId = conversationId;
        entity.channelConnectionId = channelConnectionId;
        entity.idempotencyKey = idempotencyKey;
        entity.direction = DirecaoMensagem.OUTBOUND;
        entity.contentType = contentType;
        entity.textContent = texto;
        // Nasce PENDING e sem external_id: quem preenche os dois é o worker,
        // depois que o provedor aceitar. Gravar como enviada antes disso faria
        // a tela mentir para o atendente.
        entity.status = StatusMensagem.PENDING;
        entity.authorUserId = autorId;
        // A coluna mantem o default do banco como fonte persistida, mas a API
        // responde antes de uma nova leitura. Sem o valor local, o envio e
        // aceito e enfileirado, porem o frontend recebe criadaEm=null e exibe
        // uma falha falsa mesmo com a mensagem entregue pelo worker.
        entity.createdAt = Instant.now();
        entity.createdBy = autorId;
        entity.updatedBy = autorId;
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getConversationId() {
        return conversationId;
    }

    UUID getChannelConnectionId() {
        return channelConnectionId;
    }

    String getExternalId() {
        return externalId;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    DirecaoMensagem getDirection() {
        return direction;
    }

    TipoConteudo getContentType() {
        return contentType;
    }

    String getTextContent() {
        return textContent;
    }

    StatusMensagem getStatus() {
        return status;
    }

    UUID getAuthorUserId() {
        return authorUserId;
    }

    Instant getSentAt() {
        return sentAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    long getVersion() {
        return version;
    }

    String getFailureReason() {
        return failureReason;
    }

    void marcarEnviada(String externalIdDoProvedor) {
        this.externalId = externalIdDoProvedor;
        this.status = StatusMensagem.SENT;
        this.sentAt = Instant.now();
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.deadLetteredAt = null;
    }

    void marcarFalha(String motivoSanitizado) {
        this.status = StatusMensagem.FAILED;
        this.failureReason = motivoSanitizado;
    }

    void marcarFalhaPermanente(String motivoSanitizado) {
        marcarFalha(motivoSanitizado);
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.deadLetteredAt = Instant.now();
    }

    void adiar(java.time.Duration espera) {
        Instant minimo = Instant.now().plus(espera);
        if (this.nextAttemptAt == null || this.nextAttemptAt.isBefore(minimo)) {
            this.nextAttemptAt = minimo;
        }
        this.leaseUntil = this.nextAttemptAt;
    }
}
