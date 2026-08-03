package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.CredenciaisInvalidasException;
import br.com.pnp.crm.shared.api.DomainException;
import br.com.pnp.crm.shared.api.ErroResponse;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.SessaoExpiradaException;
import br.com.pnp.crm.shared.api.TenantNaoDefinidoException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Traduz falhas para RFC 9457 sem expor detalhe interno. */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String INTERNAL_DETAIL =
            "Não foi possível concluir a operação. Tente novamente.";

    @ExceptionHandler(CredenciaisInvalidasException.class)
    ResponseEntity<ErroResponse> invalidCredentials(
            CredenciaisInvalidasException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception, request, correlation(request));
    }

    @ExceptionHandler(SessaoExpiradaException.class)
    ResponseEntity<ErroResponse> expiredSession(
            SessaoExpiradaException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception, request, correlation(request));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<ErroResponse> denied(
            AcessoNegadoException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, exception, request, correlation(request));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ErroResponse> notFound(
            RecursoNaoEncontradoException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception, request, correlation(request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResponse> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        String correlationId = correlation(request);
        List<ErroResponse.ErroCampo> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErroResponse.ErroCampo(error.getField(), error.getDefaultMessage()))
                .toList();
        ErroResponse body = new ErroResponse(
                URI.create("/problemas/validacao"), "Requisição inválida", 400,
                "Verifique os campos informados.", instance(request), "VALIDACAO",
                correlationId, fields, Instant.now());
        return problem(HttpStatus.BAD_REQUEST, correlationId, body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErroResponse> invalidJson(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        String correlationId = correlation(request);
        log.debug("Corpo JSON inválido. correlacaoId={}", correlationId);
        return problem(HttpStatus.BAD_REQUEST, correlationId,
                ErroResponse.de(400, "Requisição inválida", "JSON_INVALIDO",
                        "Corpo da requisição inválido.", correlationId, instance(request)));
    }

    @ExceptionHandler(TenantNaoDefinidoException.class)
    ResponseEntity<ErroResponse> missingTenant(
            TenantNaoDefinidoException exception, HttpServletRequest request) {
        String correlationId = correlation(request);
        log.error("Tenant ausente em operação protegida. correlacaoId={}", correlationId, exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, correlationId,
                ErroResponse.de(500, "Erro interno", "ERRO_INTERNO", INTERNAL_DETAIL,
                        correlationId, instance(request)));
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErroResponse> domain(
            DomainException exception, HttpServletRequest request) {
        String correlationId = correlation(request);
        log.warn("Regra de domínio violada. correlacaoId={} codigo={}",
                correlationId, exception.getCodigo());
        HttpStatus status = statusForDomain(exception);
        return response(status, exception, request, correlationId);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResponse> unexpected(Exception exception, HttpServletRequest request) {
        String correlationId = correlation(request);
        log.error("Erro não tratado. correlacaoId={}", correlationId, exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, correlationId,
                ErroResponse.de(500, "Erro interno", "ERRO_INTERNO", INTERNAL_DETAIL,
                        correlationId, instance(request)));
    }

    private ResponseEntity<ErroResponse> response(HttpStatus status, DomainException exception,
                                                  HttpServletRequest request, String correlationId) {
        return problem(status, correlationId,
                ErroResponse.de(status.value(), statusTitle(status), exception.getCodigo(),
                        exception.getMessage(), correlationId, instance(request)));
    }

    private ResponseEntity<ErroResponse> problem(HttpStatus status, String correlationId,
                                                 ErroResponse body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(CORRELATION_HEADER, correlationId)
                .body(body);
    }

    private static HttpStatus statusForDomain(DomainException exception) {
        if ("PERFIL_INICIAL_JA_CONCLUIDO".equals(exception.getCodigo())) {
            return HttpStatus.CONFLICT;
        }
        if (exception.getCodigo().endsWith("_NAO_ENCONTRADA")) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    private static URI instance(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }

    private static String statusTitle(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Requisição inválida";
            case UNAUTHORIZED -> "Autenticação necessária";
            case FORBIDDEN -> "Acesso negado";
            case NOT_FOUND -> "Recurso não encontrado";
            case CONFLICT -> "Conflito";
            case TOO_MANY_REQUESTS -> "Muitas requisições";
            case UNPROCESSABLE_ENTITY -> "Regra de negócio não atendida";
            default -> "Falha na requisição";
        };
    }

    private static String correlation(HttpServletRequest request) {
        String recebido = request.getHeader(CORRELATION_HEADER);
        if (recebido != null) {
            try {
                return UUID.fromString(recebido).toString();
            } catch (IllegalArgumentException ignored) {
                // Entrada não confiável: substitui, não registra nem reflete.
            }
        }
        return UUID.randomUUID().toString();
    }
}
