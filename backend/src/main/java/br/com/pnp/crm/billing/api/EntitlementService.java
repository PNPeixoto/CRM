package br.com.pnp.crm.billing.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta tenant-scoped para avaliar contratacao e registrar consumo.
 *
 * <p>Esta API responde somente pelos dois eixos que pertencem ao modulo:
 * disponibilidade tecnica e concessao contratual. Permissao do usuario deve
 * continuar sendo exigida pelo modulo {@code authz}; visibilidade de navegacao
 * continua sendo apresentacao. Um resultado contratado nunca concede uma
 * acao ao usuario.
 */
public interface EntitlementService {

    AvaliacaoCapacidade avaliar(String capabilityCode, Instant instante);

    ResultadoMedicao medir(EventoDeUso evento);

    Optional<AgregadoDeUso> agregar(String metricCode, Instant referencia);

    List<FonteDeUso> listarFontes(
            UUID entitlementGrantId,
            String metricCode,
            Instant inicioInclusivo,
            Instant fimExclusivo);

    record AvaliacaoCapacidade(
            DisponibilidadeTecnica disponibilidade,
            Contratacao contratacao,
            UUID entitlementGrantId,
            Integer versionNumber,
            Instant validFrom,
            Instant validUntil) {
        public AvaliacaoCapacidade {
            Objects.requireNonNull(disponibilidade);
            Objects.requireNonNull(contratacao);
            if ((contratacao == Contratacao.CONTRATADA) != (entitlementGrantId != null)) {
                throw new IllegalArgumentException("Contratacao e concessao precisam ser coerentes.");
            }
        }
    }

    record EventoDeUso(
            String metricCode,
            long quantity,
            String sourceType,
            String sourceId,
            String idempotencyKey,
            Instant occurredAt) {
        public EventoDeUso {
            exigirCodigo(metricCode, "Metrica");
            if (quantity <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
            exigirTipoFonte(sourceType);
            exigirTexto(sourceId, "Fonte");
            exigirTexto(idempotencyKey, "Chave idempotente");
            Objects.requireNonNull(occurredAt, "Instante da ocorrencia e obrigatorio.");
        }
    }

    record ResultadoMedicao(
            UUID usageEventId,
            ResultadoRegistro resultado,
            UUID entitlementGrantId,
            Instant windowStartedAt,
            Instant windowEndedAt,
            Long totalQuantity) {
        public ResultadoMedicao {
            Objects.requireNonNull(resultado);
        }
    }

    record AgregadoDeUso(
            UUID entitlementGrantId,
            int versionNumber,
            String metricCode,
            Instant windowStartedAt,
            Instant windowEndedAt,
            long totalQuantity,
            long eventCount) {
        public AgregadoDeUso {
            Objects.requireNonNull(entitlementGrantId);
            exigirCodigo(metricCode, "Metrica");
            Objects.requireNonNull(windowStartedAt);
            if (totalQuantity < 0 || eventCount < 0) {
                throw new IllegalArgumentException("Agregado nao pode ser negativo.");
            }
        }
    }

    record FonteDeUso(
            UUID usageEventId,
            String sourceType,
            String sourceId,
            long quantity,
            Instant occurredAt,
            String idempotencyKey) {
        public FonteDeUso {
            Objects.requireNonNull(usageEventId);
            exigirTipoFonte(sourceType);
            exigirTexto(sourceId, "Fonte");
            if (quantity <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
            Objects.requireNonNull(occurredAt);
            exigirTexto(idempotencyKey, "Chave idempotente");
        }
    }

    enum DisponibilidadeTecnica {
        DISPONIVEL,
        INDISPONIVEL,
        DESCONHECIDA
    }

    enum Contratacao {
        CONTRATADA,
        NAO_CONTRATADA
    }

    enum ResultadoRegistro {
        RECORDED,
        REPLAY,
        MODULE_UNAVAILABLE,
        NOT_CONTRACTED,
        SOFT_LIMIT_EXCEEDED,
        HARD_LIMIT_GRACE,
        HARD_LIMIT_EXCEEDED
    }

    private static void exigirCodigo(String valor, String nome) {
        if (valor == null || !valor.matches("^[A-Z][A-Z0-9_]{2,79}$")) {
            throw new IllegalArgumentException(nome + " invalido.");
        }
    }

    private static void exigirTipoFonte(String valor) {
        if (valor == null || !valor.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            throw new IllegalArgumentException("Tipo de fonte invalido.");
        }
    }

    private static void exigirTexto(String valor, String nome) {
        if (valor == null || valor.isBlank() || valor.length() > 200) {
            throw new IllegalArgumentException(nome + " invalido.");
        }
    }
}
