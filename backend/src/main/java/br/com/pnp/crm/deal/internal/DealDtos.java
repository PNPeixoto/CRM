package br.com.pnp.crm.deal.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class DealDtos {

    private DealDtos() {
    }

    @Schema(name = "DealFunilResponse")
    record FunilResponse(UUID id, String nome, boolean padrao, List<EtapaResponse> etapas) {
    }

    @Schema(name = "DealEtapaResponse")
    record EtapaResponse(UUID id, String nome, int posicao, boolean ganho, boolean perda) {
    }

    /**
     * O valor trafega em <b>centavos</b>, como inteiro, do banco até a tela.
     * A conversão para "R$ 1.234,56" acontece só na formatação de exibição.
     * Aceitar decimal aqui reintroduziria ponto flutuante na fronteira, que é
     * exatamente onde o erro de arredondamento entra.
     */
    record OportunidadeRequest(
            @NotBlank(message = "Informe o título.")
            @Size(max = 200)
            String titulo,

            @NotNull(message = "Informe a etapa.")
            UUID etapaId,

            @PositiveOrZero(message = "O valor não pode ser negativo.")
            long valorCentavos,

            UUID contatoId,
            LocalDate previsaoFechamento,
            UUID responsavelId) {
    }

    record MoverRequest(@NotNull(message = "Informe a etapa de destino.") UUID etapaId,
                        @Size(max = 500) String motivoPerda) {
    }

    record OportunidadeResponse(
            UUID id,
            UUID funilId,
            UUID etapaId,
            UUID contatoId,
            String titulo,
            long valorCentavos,
            String status,
            LocalDate previsaoFechamento,
            String motivoPerda,
            UUID responsavelId,
            String responsavelLogin,
            String responsavelNome,
            Instant criadaEm) {
    }
}
