package br.com.pnp.crm.contact.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

interface ContactRepository extends JpaRepository<ContactEntity, UUID> {

    boolean existsByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<ContactEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<ContactEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    /** Serializa apenas criações com a mesma chave; o índice único fecha a última janela. */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    int bloquearCriacaoIdempotente(@Param("lockId") long lockId);

    /**
     * Busca por nome, e-mail ou empresa.
     *
     * <p>O termo entra como parâmetro, nunca concatenado — mesmo aqui, onde
     * "é só um LIKE". Concatenação em filtro dinâmico é a origem mais comum de
     * injeção em CRM, porque o campo de busca aceita qualquer coisa.
     */
    /**
     * @param irrestrito   verdadeiro quando o alcance é todo o tenant
     * @param responsaveis ids permitidos quando o alcance é equipe ou próprio.
     *                     O filtro é aplicado <b>na consulta</b>, e não sobre a
     *                     página já carregada: filtrar depois devolveria
     *                     páginas de tamanho imprevisível e ainda faria o
     *                     banco ler o que o usuário não pode ver.
     */
    @Query("""
            SELECT c FROM ContactEntity c
             WHERE c.tenantId = :tenantId
               AND c.deletedAt IS NULL
               AND (:irrestrito = TRUE OR c.ownerUserId IN :responsaveis)
               AND (CAST(:termo AS string) IS NULL
                    OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%'))
                    OR LOWER(c.email) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%'))
                    OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%')))
             ORDER BY c.name
            """)
    Page<ContactEntity> buscar(@Param("tenantId") UUID tenantId,
                               @Param("irrestrito") boolean irrestrito,
                               @Param("responsaveis") Collection<UUID> responsaveis,
                               @Param("termo") String termo,
                               Pageable pageable);

    long countByTenantIdAndDeletedAtIsNull(UUID tenantId);
}
