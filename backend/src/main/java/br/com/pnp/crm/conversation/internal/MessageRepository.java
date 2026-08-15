package br.com.pnp.crm.conversation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Pageable;

interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    Optional<MessageEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /**
     * Checagem de idempotência antes da inserção. Não substitui a constraint
     * do banco: entre esta consulta e o INSERT existe uma janela, e duas
     * entregas simultâneas do mesmo webhook passariam as duas por aqui. A
     * consulta evita o caso comum; a constraint resolve a corrida.
     */
    Optional<MessageEntity> findByTenantIdAndChannelConnectionIdAndExternalId(
            UUID tenantId, UUID channelConnectionId, String externalId);

    Optional<MessageEntity> findByTenantIdAndConversationIdAndIdempotencyKey(
            UUID tenantId, UUID conversationId, String idempotencyKey);

    /**
     * Serializa somente operações com a mesma chave. O índice único continua
     * sendo a última defesa; o advisory lock fecha a corrida entre consultar e
     * inserir sem bloquear envios independentes.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    int bloquearIdempotencia(@Param("lockId") long lockId);

    /**
     * Página keyset em ordem reversa. O serviço inverte o lote para manter o
     * contrato cronológico já consumido pela interface.
     */
    List<MessageEntity> findByTenantIdAndConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            UUID tenantId, UUID conversationId, Pageable pageable);

    /**
     * Paginas seguintes sempre recebem cursor completo. Separar a primeira
     * pagina evita enviar um parametro temporal nulo ao PostgreSQL: dentro de
     * {@code :cursor IS NULL OR ...}, o driver nao consegue inferir seu tipo.
     */
    @Query("""
            SELECT m FROM MessageEntity m
             WHERE m.tenantId = :tenantId
               AND m.conversationId = :conversationId
               AND m.deletedAt IS NULL
               AND (m.createdAt < :cursorCriadoEm
                    OR (m.createdAt = :cursorCriadoEm AND m.id < :cursorId))
             ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<MessageEntity> buscarPaginaAnteriorDoHistorico(
            @Param("tenantId") UUID tenantId,
            @Param("conversationId") UUID conversationId,
            @Param("cursorCriadoEm") Instant cursorCriadoEm,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    /**
     * Devolve só a coluna necessária, e não a conversa inteira. Carregar a
     * entidade traria a conversa para o contexto de persistência e qualquer
     * alteração acidental nela seria gravada no flush — efeito colateral
     * invisível numa operação que deveria ser de leitura.
     */
    @Query("""
            SELECT c.externalContactId
              FROM ConversationEntity c
             WHERE c.tenantId = :tenantId
               AND c.id = :conversationId
            """)
    Optional<String> buscarExternalContactIdDaConversa(
            @Param("tenantId") UUID tenantId,
            @Param("conversationId") UUID conversationId);

    /**
     * Usa o instante em que o CRM persistiu a entrada, e não o relógio do
     * provedor, para que desvio de horário no payload não prolongue uma janela
     * de atendimento regulada pelo canal.
     */
    @Query("""
            SELECT MAX(m.createdAt) FROM MessageEntity m
             WHERE m.tenantId = :tenantId
               AND m.conversationId = :conversationId
               AND m.direction = br.com.pnp.crm.conversation.api.DirecaoMensagem.INBOUND
               AND m.deletedAt IS NULL
            """)
    Optional<Instant> buscarUltimoRecebimentoDaConversa(
            @Param("tenantId") UUID tenantId,
            @Param("conversationId") UUID conversationId);

    @Query("""
            SELECT m.conversationId AS conversationId,
                   MAX(m.createdAt) AS recebidaEm
              FROM MessageEntity m
             WHERE m.tenantId = :tenantId
               AND m.conversationId IN :conversationIds
               AND m.direction = br.com.pnp.crm.conversation.api.DirecaoMensagem.INBOUND
               AND m.deletedAt IS NULL
             GROUP BY m.conversationId
            """)
    List<UltimoRecebimentoDaConversa> buscarUltimosRecebimentosDasConversas(
            @Param("tenantId") UUID tenantId,
            @Param("conversationIds") List<UUID> conversationIds);

    interface UltimoRecebimentoDaConversa {
        UUID getConversationId();

        Instant getRecebidaEm();
    }

    @Query("""
            SELECT COUNT(m) FROM MessageEntity m
             WHERE m.tenantId = :tenantId
               AND m.direction = br.com.pnp.crm.conversation.api.DirecaoMensagem.INBOUND
               AND m.createdAt >= :desde
               AND m.deletedAt IS NULL
            """)
    long contarRecebidasDesde(@Param("tenantId") UUID tenantId,
                              @Param("desde") java.time.Instant desde);
}
