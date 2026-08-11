package br.com.pnp.crm.audit.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Porta minima para registrar eventos canonicos de auditoria.
 *
 * <p>O contrato nao aceita texto livre. Isso impede que payload, mensagem,
 * token ou segredo sejam copiados para a trilha por conveniencia.
 */
public interface AuditTrail {

    void registrar(Evento evento);

    record Evento(Acao acao, Ator ator, Escopo escopo, Alvo alvo,
                  Resultado resultado, Motivo motivo) {
        public Evento {
            Objects.requireNonNull(acao);
            Objects.requireNonNull(ator);
            Objects.requireNonNull(escopo);
            Objects.requireNonNull(alvo);
            Objects.requireNonNull(resultado);
            Objects.requireNonNull(motivo);
        }
    }

    record Ator(TipoAtor tipo, UUID id) {
        public Ator {
            Objects.requireNonNull(tipo);
            if ((tipo == TipoAtor.HUMAN) != (id != null)) {
                throw new IllegalArgumentException("Ator humano exige identificador.");
            }
        }

        public static Ator humano(UUID id) {
            return new Ator(TipoAtor.HUMAN, Objects.requireNonNull(id));
        }

        public static Ator sistema() {
            return new Ator(TipoAtor.SYSTEM, null);
        }
    }

    record Escopo(TipoEscopo tipo, UUID id) {
        public Escopo {
            Objects.requireNonNull(tipo);
            Objects.requireNonNull(id);
        }

        public static Escopo tenant(UUID tenantId) {
            return new Escopo(TipoEscopo.TENANT, tenantId);
        }
    }

    record Alvo(TipoAlvo tipo, UUID id) {
        public Alvo {
            Objects.requireNonNull(tipo);
        }
    }

    enum Acao {
        AUTHORIZATION_DENIED("AUTHORIZATION_DENIED_V1", "ACCESS", true),
        AUDIT_READ("AUDIT_READ_V1", "ACCESS", false),
        EXPORT_REQUESTED("EXPORT_REQUESTED_V1", "EXPORT", false),
        EXPORT_COMPLETED("EXPORT_COMPLETED_V1", "EXPORT", false),
        CREDENTIAL_CHANGED("CREDENTIAL_CHANGED_V1", "SECURITY", false),
        ROLE_CHANGED("ROLE_CHANGED_V1", "SECURITY", false),
        SENSITIVE_CONFIGURATION_CHANGED(
                "SENSITIVE_CONFIGURATION_CHANGED_V1", "CONFIGURATION", false);

        private final String codigo;
        private final String categoriaRetencao;
        private final boolean melhorEsforco;

        Acao(String codigo, String categoriaRetencao, boolean melhorEsforco) {
            this.codigo = codigo;
            this.categoriaRetencao = categoriaRetencao;
            this.melhorEsforco = melhorEsforco;
        }

        public String codigo() {
            return codigo;
        }

        public String categoriaRetencao() {
            return categoriaRetencao;
        }

        public boolean melhorEsforco() {
            return melhorEsforco;
        }
    }

    enum TipoAtor { HUMAN, SYSTEM }

    enum TipoEscopo { TENANT, UNIT, OWN, SYSTEM }

    enum TipoAlvo {
        AUTHORIZATION,
        AUDIT_TRAIL,
        EXPORT,
        CHANNEL_CONNECTION,
        HTTP_CONNECTOR,
        ROLE,
        TEAM,
        TENANT_PROFILE
    }

    enum Resultado { SUCCEEDED, DENIED, FAILED }

    enum Motivo {
        PERMISSION_MISSING,
        MEMBERSHIP_INVALID,
        SCOPE_DENIED,
        PAGE_VIEW,
        CHANNEL_CREATED,
        CHANNEL_UPDATED,
        CHANNEL_ACTIVATION_CHANGED,
        CONNECTOR_CREATED,
        CONNECTOR_UPDATED,
        CONNECTOR_STATUS_CHANGED,
        CONNECTOR_CREDENTIAL_CHANGED,
        CHANNEL_CREDENTIAL_CHANGED,
        TENANT_PROFILE_COMPLETED,
        TENANT_PRESENTATION_CHANGED,
        EXPORT_REQUESTED,
        EXPORT_COMPLETED,
        ROLE_CREATED,
        ROLE_UPDATED,
        ROLE_REMOVED,
        ROLE_ASSIGNMENT_CHANGED,
        TEAM_MEMBERSHIP_CHANGED
    }
}
