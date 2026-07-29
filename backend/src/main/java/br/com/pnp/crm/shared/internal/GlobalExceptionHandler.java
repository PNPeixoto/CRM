package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.CredenciaisInvalidasException;
import br.com.pnp.crm.shared.api.DomainException;
import br.com.pnp.crm.shared.api.ErroResponse;
import br.com.pnp.crm.shared.api.SessaoExpiradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Traduz exceção em resposta HTTP num único lugar.
 *
 * <p>Regra que governa todo este arquivo: o cliente recebe código, mensagem
 * genérica e um id de correlação. O detalhe — stack trace, mensagem do banco,
 * qual constraint quebrou — vai só para o log. Mensagem de erro detalhada é
 * uma das fontes mais produtivas de reconhecimento: "duplicate key value
 * violates constraint app_user_email_unico_por_tenant" entrega o nome da
 * tabela, do índice e a confirmação de que aquele e-mail existe.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MENSAGEM_ERRO_INTERNO =
            "Não foi possível concluir a operação. Tente novamente.";

    @ExceptionHandler(CredenciaisInvalidasException.class)
    ResponseEntity<ErroResponse> credenciaisInvalidas(CredenciaisInvalidasException ex) {
        // Sem log de warn com o login tentado: isso transformaria o log em uma
        // lista de logins válidos e inválidos. O registro da tentativa é feito
        // na auditoria, com o cuidado devido.
        return resposta(HttpStatus.UNAUTHORIZED, ex, novaCorrelacao());
    }

    @ExceptionHandler(SessaoExpiradaException.class)
    ResponseEntity<ErroResponse> sessaoExpirada(SessaoExpiradaException ex) {
        return resposta(HttpStatus.UNAUTHORIZED, ex, novaCorrelacao());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex) {
        String correlacaoId = novaCorrelacao();
        List<ErroResponse.ErroCampo> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroResponse.ErroCampo(erro.getField(), erro.getDefaultMessage()))
                .toList();
        // Erro de formato pode voltar detalhado: o cliente precisa saber qual
        // campo corrigir, e a informação já é dele.
        return ResponseEntity.badRequest().body(new ErroResponse(
                "VALIDACAO", "Verifique os campos informados.", correlacaoId, campos, Instant.now()));
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErroResponse> dominio(DomainException ex) {
        String correlacaoId = novaCorrelacao();
        log.warn("Regra de domínio violada. correlacaoId={} codigo={}", correlacaoId, ex.getCodigo(), ex);
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, ex, correlacaoId);
    }

    /**
     * Rede de segurança. Tudo que não foi previsto vira 500 com corpo genérico.
     * O {@code catch} amplo é intencional aqui e só aqui: é a última barreira
     * antes de o Spring devolver a página de erro padrão, que inclui detalhes
     * da exceção.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResponse> naoPrevisto(Exception ex) {
        String correlacaoId = novaCorrelacao();
        log.error("Erro não tratado. correlacaoId={}", correlacaoId, ex);
        return ResponseEntity.internalServerError().body(
                ErroResponse.de("ERRO_INTERNO", MENSAGEM_ERRO_INTERNO, correlacaoId));
    }

    private ResponseEntity<ErroResponse> resposta(HttpStatus status, DomainException ex, String correlacaoId) {
        return ResponseEntity.status(status).body(
                ErroResponse.de(ex.getCodigo(), ex.getMessage(), correlacaoId));
    }

    private String novaCorrelacao() {
        return UUID.randomUUID().toString();
    }
}
