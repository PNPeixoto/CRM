package br.com.pnp.crm.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada e saída do módulo de autenticação.
 *
 * <p>Separados das entidades JPA de propósito. Reaproveitar a entidade na
 * entrada cria <i>mass assignment</i>: bastaria o cliente mandar
 * {@code "active": true} ou {@code "tenantId": "..."} para alterar campo que
 * não lhe pertence. Na saída, a entidade carregaria {@code passwordHash} para
 * dentro do JSON.
 */
final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * O slug identifica a conta contratante. Não é o {@code tenant_id}: é um
     * apelido público que o usuário digita, e por si só não autoriza nada —
     * quem autoriza é a senha. O {@code tenant_id} real nunca trafega na
     * entrada.
     */
    record LoginRequest(
            @NotBlank(message = "Informe a empresa.")
            @Size(max = 50)
            String empresa,

            @NotBlank(message = "Informe o login.")
            @Size(max = 100)
            String login,

            @NotBlank(message = "Informe a senha.")
            @Size(max = 200)
            String senha,

            @Size(max = 64)
            String codigoMfa) {
    }

    /**
     * O access token vai no corpo, para o frontend guardar em memória. O
     * refresh não aparece aqui: ele sai apenas no cookie {@code HttpOnly},
     * onde o JavaScript não alcança. Devolver o refresh no corpo anularia a
     * proteção contra XSS que o cookie existe para dar.
     */
    record SessaoResponse(
            String accessToken,
            long expiraEmSegundos,
            UsuarioResponse usuario,
            boolean mfaVerificado) {
    }

    /**
     * O {@code tenantId} sai daqui porque o cliente precisa dele para montar o
     * destino STOMP em que vai se inscrever. Não é vazamento: ele já está no
     * claim {@code tid} do token que o próprio cliente carrega, e conhecê-lo
     * não autoriza nada — quem autoriza a inscrição é o backend, comparando o
     * destino pedido com o tenant do token verificado.
     */
    record UsuarioResponse(UUID id, UUID tenantId, String login, String nomeCompleto) {
    }

    record MfaEnrollmentResponse(String desafio, String segredo,
                                 String otpauthUri, long expiraEmSegundos) {
    }

    record MfaActivationRequest(
            @NotBlank @Size(max = 100) String desafio,
            @NotBlank @Size(max = 64) String codigo) {
    }

    record MfaActivationResponse(SessaoResponse sessao, List<String> codigosRecuperacao) {
        MfaActivationResponse {
            codigosRecuperacao = List.copyOf(codigosRecuperacao);
        }
    }

    record PasswordResetRequest(
            @NotBlank @Size(max = 50) String empresa,
            @NotBlank @Size(max = 254) String identificador) {
    }

    record PasswordResetConfirmation(
            @NotBlank @Size(max = 100) String codigo,
            @NotBlank @Size(max = PasswordPolicy.MAXIMUM_LENGTH) String novaSenha) {
    }
}
