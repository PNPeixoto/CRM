package br.com.pnp.crm.privacy.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Direitos do titular: acesso e eliminacao.
 *
 * <p>Duas garantias atravessam os dois endpoints.
 *
 * <p><b>Identidade e autorizacao antes de qualquer coisa.</b> Quem pede e
 * identificado pelo token verificado, precisa de {@code privacy.manage} e
 * precisa do alcance de tenant — dado de titular nao e recurso de um
 * responsavel, e recortar por "meus contatos" produziria resposta incompleta a
 * um direito.
 *
 * <p><b>Tudo auditado, nada guardado.</b> A trilha registra que houve pedido,
 * quem pediu, sobre quem e o resultado — nunca o conteudo exportado. E a
 * resposta e {@code no-store}: um documento com o dado pessoal inteiro de uma
 * pessoa nao pode ficar em cache de navegador ou de proxy.
 */
@RestController
@RequestMapping("/api/privacidade")
class PrivacidadeController {

    private final DireitosDoTitularService direitos;
    private final Autorizacao autorizacao;
    private final AuditTrail audit;
    private final Clock relogio;

    @Autowired
    PrivacidadeController(DireitosDoTitularService direitos, Autorizacao autorizacao,
                          AuditTrail audit) {
        this(direitos, autorizacao, audit, Clock.systemUTC());
    }

    /** Relógio explícito para teste; o de produção usa o construtor acima. */
    PrivacidadeController(DireitosDoTitularService direitos, Autorizacao autorizacao,
                          AuditTrail audit, Clock relogio) {
        this.direitos = direitos;
        this.autorizacao = autorizacao;
        this.audit = audit;
        this.relogio = relogio;
    }

    @PostMapping("/titulares/{contatoId}/exportacao")
    @Transactional
    ResponseEntity<ExportacaoDeTitular> exportar(@PathVariable UUID contatoId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.PRIVACY_MANAGE);
        UUID solicitante = UUID.fromString(jwt.getSubject());
        Instant agora = relogio.instant();

        // Registrado ANTES de montar o documento: se a geracao falhar no meio,
        // o pedido continua tendo existido, e um pedido de titular sem rastro e
        // exatamente o que a trilha existe para impedir.
        auditar(AuditTrail.Acao.EXPORT_REQUESTED, AuditTrail.Motivo.EXPORT_REQUESTED,
                contatoId, solicitante, AuditTrail.Resultado.SUCCEEDED);

        ExportacaoDeTitular exportacao = direitos.exportar(contatoId, solicitante, agora);

        auditar(AuditTrail.Acao.EXPORT_COMPLETED, AuditTrail.Motivo.EXPORT_COMPLETED,
                contatoId, solicitante, AuditTrail.Resultado.SUCCEEDED);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(exportacao);
    }

    @PostMapping("/titulares/{contatoId}/anonimizacao")
    @Transactional
    ResponseEntity<Void> anonimizar(@PathVariable UUID contatoId,
                                    @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.PRIVACY_MANAGE);
        UUID solicitante = UUID.fromString(jwt.getSubject());

        try {
            direitos.anonimizar(contatoId, relogio.instant());
        } catch (EliminacaoRecusadaException recusa) {
            // A recusa tambem e registrada. Um pedido negado por retencao legal
            // e justamente o que precisa ficar demonstravel depois.
            auditar(AuditTrail.Acao.SENSITIVE_CONFIGURATION_CHANGED,
                    AuditTrail.Motivo.EXPORT_REQUESTED,
                    contatoId, solicitante, AuditTrail.Resultado.DENIED);
            throw recusa;
        }

        auditar(AuditTrail.Acao.SENSITIVE_CONFIGURATION_CHANGED,
                AuditTrail.Motivo.EXPORT_COMPLETED,
                contatoId, solicitante, AuditTrail.Resultado.SUCCEEDED);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .build();
    }

    private void auditar(AuditTrail.Acao acao, AuditTrail.Motivo motivo, UUID alvo,
                         UUID autor, AuditTrail.Resultado resultado) {
        audit.registrar(new AuditTrail.Evento(
                acao,
                AuditTrail.Ator.humano(autor),
                AuditTrail.Escopo.tenant(TenantContext.obrigatorio()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.EXPORT, alvo),
                resultado, motivo));
    }
}
