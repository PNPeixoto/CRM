package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contact")
class ContactEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    @Column(name = "company_name")
    private String companyName;

    private String notes;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

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

    protected ContactEntity() {
    }

    static ContactEntity novo(UUID tenantId, UUID autorId) {
        ContactEntity entity = new ContactEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.createdBy = autorId;
        entity.updatedBy = autorId;
        return entity;
    }

    void aplicar(String name, String email, String phone, String companyName,
                 String notes, UUID ownerUserId, UUID autorId) {
        this.name = name;
        // Normaliza para vazio→null: string em branco no banco vira um valor
        // que parece preenchido em toda consulta e não é.
        this.email = normalizar(email);
        this.phone = normalizar(phone);
        this.companyName = normalizar(companyName);
        this.notes = normalizar(notes);
        this.ownerUserId = ownerUserId;
        this.updatedBy = autorId;
    }

    /** Exclusão lógica: o histórico de conversas e negócios continua existindo. */
    void excluir(UUID autorId) {
        this.deletedAt = Instant.now();
        this.updatedBy = autorId;
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getEmail() {
        return email;
    }

    String getPhone() {
        return phone;
    }

    String getCompanyName() {
        return companyName;
    }

    String getNotes() {
        return notes;
    }

    UUID getOwnerUserId() {
        return ownerUserId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
