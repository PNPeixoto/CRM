package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AutomationModel {

    private AutomationModel() {
    }

    record DefinicaoRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{2,79}$") String codigo,
            @NotBlank @Size(max = 200) String nome,
            @Min(1) @Max(20) int limiteConcorrencia,
            @NotNull @Valid EspecificacaoRequest especificacao) {
    }

    record NovaVersaoRequest(
            @NotBlank @Size(max = 200) String nome,
            @Min(1) @Max(20) int limiteConcorrencia,
            @NotNull @Valid EspecificacaoRequest especificacao) {
    }

    record EspecificacaoRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,79}$") String gatilho,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{0,79}$") String passoInicial,
            @Min(1) @Max(86400) int duracaoMaximaSegundos,
            @NotEmpty @Size(max = 500) List<@Valid PassoRequest> passos) {
    }

    record PassoRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{0,79}$") String chave,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,69}_V[0-9]+$") String acao,
            @Min(1) @Max(5) int maxTentativas,
            @Size(max = 100) List<@Pattern(regexp = "^[a-z][a-z0-9_-]{0,79}$") String> proximos,
            @Size(max = 10) Map<
                    @Pattern(regexp = "^[a-z][a-zA-Z0-9]{0,39}$") String,
                    @Size(max = 500) String> configuracao) {

        PassoRequest {
            configuracao = configuracao == null ? Map.of() : Map.copyOf(configuracao);
        }

        PassoRequest(String chave, String acao, int maxTentativas, List<String> proximos) {
            this(chave, acao, maxTentativas, proximos, Map.of());
        }
    }

    /** Snapshot persistido; efeito e retry ficam congelados junto da versao. */
    record EspecificacaoSnapshot(
            String gatilho,
            String passoInicial,
            int duracaoMaximaSegundos,
            List<PassoSnapshot> passos) {

        PassoSnapshot passo(String chave) {
            return passos.stream()
                    .filter(passo -> passo.chave().equals(chave))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Passo da versao nao encontrado."));
        }
    }

    record PassoSnapshot(
            String chave,
            String acao,
            AutomationAction.NaturezaEfeito efeito,
            boolean repeticaoSegura,
            int maxTentativas,
            List<String> proximos,
            Map<String, String> configuracao) {

        PassoSnapshot {
            proximos = proximos == null ? List.of() : List.copyOf(proximos);
            configuracao = configuracao == null ? Map.of() : Map.copyOf(configuracao);
        }

        PassoSnapshot(String chave, String acao, AutomationAction.NaturezaEfeito efeito,
                      boolean repeticaoSegura, int maxTentativas, List<String> proximos) {
            this(chave, acao, efeito, repeticaoSegura, maxTentativas, proximos, Map.of());
        }
    }

    record DefinicaoResumo(
            UUID id,
            String codigo,
            String nome,
            String status,
            int versaoAtual,
            Integer versaoAtiva,
            int limiteConcorrencia,
            Instant criadaEm,
            Instant atualizadaEm) {
    }

    record VersaoResumo(
            UUID id,
            int numero,
            String gatilho,
            String hash,
            int duracaoMaximaSegundos,
            int quantidadePassos,
            int quantidadeRamificacoes,
            Instant criadaEm) {
    }

    record ExecucaoResumo(
            UUID id,
            UUID definicaoId,
            int versaoDefinicao,
            String gatilho,
            boolean simulacao,
            String status,
            String codigoFalha,
            Instant limiteEm,
            Instant iniciadaEm,
            Instant finalizadaEm,
            Instant criadaEm) {
    }

    record TransicaoResumo(
            UUID id,
            UUID passoId,
            String entidade,
            String estadoAnterior,
            String novoEstado,
            String motivo,
            String ator,
            Instant ocorridaEm) {
    }

    record DisparoRequest(
            @NotBlank @Size(max = 160)
            @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String chaveIdempotencia,
            UUID referenciaOrigem) {
    }
}
