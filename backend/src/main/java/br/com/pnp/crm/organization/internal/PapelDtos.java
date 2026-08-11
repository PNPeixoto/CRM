package br.com.pnp.crm.organization.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Contratos da administração de papéis. */
final class PapelDtos {

    private PapelDtos() {
    }

    /**
     * Alcances que a API aceita conceder.
     *
     * <p>UNIT existe em {@code membership_scope} e e deliberadamente omitido:
     * enquanto nenhuma tabela de dominio declarar unidade, uma atribuicao UNIT
     * e aceita pelo banco e <b>nao concede nada</b> (ADR-0008). Ofere-cê-la na
     * tela produziria papel que parece funcionar e falha em silencio — pior que
     * a ausencia da opcao.
     */
    static final String ALCANCES = "TENANT|OWN";

    record PapelResponse(UUID id, String codigo, String nome, String descricao,
                         boolean sistema, boolean ativo, List<String> permissoes,
                         boolean gerenciavel, long atribuicoes) {
        PapelResponse {
            permissoes = List.copyOf(permissoes);
        }
    }

    /**
     * Papéis do tenant e o catálogo de permissões marcado por delegabilidade.
     *
     * <p>A marcação existe para a tela desabilitar o que o autor não pode
     * conceder, em vez de deixar tentar e receber 422. É conveniência: a
     * decisão continua no backend, a cada chamada.
     */
    record CatalogoResponse(List<PapelResponse> papeis, List<PermissaoResponse> permissoes) {
        CatalogoResponse {
            papeis = List.copyOf(papeis);
            permissoes = List.copyOf(permissoes);
        }
    }

    record PermissaoResponse(String codigo, boolean delegavelNoTenant, boolean delegavelProprio) {
    }

    record CriarPapelRequest(
            // Mesmo formato do CHECK de app_role. Validar aqui devolve 400 com o
            // campo; deixar para o banco devolveria 500 sem indicação nenhuma.
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,48}$",
                    message = "Use letras maiúsculas, números e _, de 2 a 49 caracteres.")
            String codigo,
            @NotBlank @Size(max = 120) String nome,
            @Size(max = 500) String descricao,
            @NotNull List<@Pattern(regexp = "^[a-z][a-z0-9_.:-]{2,100}$") String> permissoes) {
        CriarPapelRequest {
            permissoes = permissoes == null ? List.of() : List.copyOf(permissoes);
        }
    }

    /** O código não entra: renomear é rotina, trocar identidade não é. */
    record AtualizarPapelRequest(@NotBlank @Size(max = 120) String nome,
                                 @Size(max = 500) String descricao,
                                 @NotNull Boolean ativo) {
    }

    /**
     * Conjunto completo, não incremento.
     *
     * <p>Um endpoint de "adicionar permissão" obrigaria a tela a calcular a
     * diferença e produziria estado intermediário se uma chamada falhasse no
     * meio de várias.
     */
    record PermissoesRequest(
            @NotNull List<@Pattern(regexp = "^[a-z][a-z0-9_.:-]{2,100}$") String> permissoes) {
        PermissoesRequest {
            permissoes = permissoes == null ? List.of() : List.copyOf(permissoes);
        }
    }

    record AtribuirPapelRequest(@NotNull UUID papelId,
                                @NotNull @Pattern(regexp = ALCANCES) String alcance) {
    }

    record MembroResponse(UUID membershipId, UUID usuarioId, String login, String nome,
                          List<AtribuicaoResponse> atribuicoes) {
        MembroResponse {
            atribuicoes = List.copyOf(atribuicoes);
        }
    }

    record AtribuicaoResponse(UUID id, UUID papelId, String papelCodigo, String papelNome,
                              String alcance) {
    }
}
