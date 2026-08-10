package br.com.pnp.crm.tenant.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Evento síncrono que materializa defaults derivados do primeiro preset.
 *
 * <p>O evento mantém o módulo tenant sem conhecer quem cria o funil. O
 * consumidor deve propagar qualquer falha para que perfil e defaults sejam
 * confirmados na mesma transação.
 */
public record PerfilInicialConcluido(
        UUID tenantId,
        UUID autorId,
        BusinessSegment segmento,
        int versaoPreset,
        DefaultPipelineTemplate funilPadrao) {

    public PerfilInicialConcluido {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(autorId, "autorId");
        Objects.requireNonNull(segmento, "segmento");
        Objects.requireNonNull(funilPadrao, "funilPadrao");
        if (versaoPreset < 1) {
            throw new IllegalArgumentException("A versão do preset deve ser positiva.");
        }
    }
}
