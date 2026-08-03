package br.com.pnp.crm.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
class AppUserEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String login;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /**
     * Hash Argon2id em formato PHC. Fica com visibilidade de pacote e sem
     * getter público de propósito: o valor não deve sair do módulo
     * {@code identity} nem por acidente de serialização.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active;

    // insertable/updatable false: quem preenche é o DEFAULT e a trigger do
    // banco. Deixar o Hibernate escrever aqui criaria duas fontes da verdade
    // para o mesmo dado, e a do banco venceria de qualquer forma.
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

    protected AppUserEntity() {
        // exigido pelo JPA
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getLogin() {
        return login;
    }

    String getEmail() {
        return email;
    }

    String getFullName() {
        return fullName;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    void changePasswordHash(String newPasswordHash, UUID changedBy) {
        this.passwordHash = newPasswordHash;
        this.updatedBy = changedBy;
    }

    boolean isActive() {
        return active;
    }
}
