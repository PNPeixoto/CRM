package br.com.pnp.crm.contact.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface ContactRepository extends JpaRepository<ContactEntity, UUID> {

    Optional<ContactEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /**
     * Busca por nome, e-mail ou empresa.
     *
     * <p>O termo entra como parâmetro, nunca concatenado — mesmo aqui, onde
     * "é só um LIKE". Concatenação em filtro dinâmico é a origem mais comum de
     * injeção em CRM, porque o campo de busca aceita qualquer coisa.
     */
    @Query("""
            SELECT c FROM ContactEntity c
             WHERE c.tenantId = :tenantId
               AND c.deletedAt IS NULL
               AND (:termo IS NULL
                    OR LOWER(c.name) LIKE LOWER(CONCAT('%', :termo, '%'))
                    OR LOWER(c.email) LIKE LOWER(CONCAT('%', :termo, '%'))
                    OR LOWER(c.companyName) LIKE LOWER(CONCAT('%', :termo, '%')))
             ORDER BY c.name
            """)
    Page<ContactEntity> buscar(@Param("tenantId") UUID tenantId,
                               @Param("termo") String termo,
                               Pageable pageable);

    long countByTenantIdAndDeletedAtIsNull(UUID tenantId);
}
