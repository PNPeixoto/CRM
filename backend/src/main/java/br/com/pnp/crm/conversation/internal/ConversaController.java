package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.time.Instant;

/**
 * REST de conversa.
 *
 * <p>É a fonte da verdade da interface. O WebSocket entrega atualização
 * incremental enquanto a conexão está de pé; ao carregar a tela ou reconectar,
 * o cliente vem aqui. Uma interface que dependa exclusivamente do socket fica
 * silenciosamente desatualizada na primeira queda de rede, e o usuário não tem
 * como saber que está vendo estado velho.
 *
 * <p>O tenant não aparece em nenhuma assinatura: ele já foi resolvido pelo
 * {@code TenantContextFilter} a partir do token verificado, e é aplicado no
 * banco pelo {@code TenantAwareDataSource}.
 */
@RestController
@RequestMapping("/api/conversas")
class ConversaController {

    private final ConversaService conversas;
    private final Autorizacao autorizacao;

    ConversaController(ConversaService conversas, Autorizacao autorizacao) {
        this.conversas = conversas;
        this.autorizacao = autorizacao;
    }

    /**
     * Conversa tem atendente atribuído, mas a caixa de entrada é compartilhada
     * por desenho: a fila só funciona se quem está livre enxergar o que ainda
     * não tem dono. Por isso estes endpoints exigem que a permissão tenha
     * alcance de tenant. Uma concessão OWN não é promovida a acesso coletivo;
     * ela só poderá ser usada quando existir uma inbox individual filtrada.
     */
    @GetMapping
    ResponseEntity<ConversationDtos.PaginaConversas> listar(
            @RequestParam(required = false) Instant antesDe,
            @RequestParam(required = false) UUID antesDoId,
            @RequestParam(defaultValue = "50") int limite,
            @RequestParam(required = false) String busca) {
        autorizacao.exigirNoTenant(Permissao.CONVERSATIONS_READ);
        return ResponseEntity.ok(conversas.listar(antesDe, antesDoId, limite, busca));
    }

    @GetMapping("/{conversationId}/mensagens")
    ResponseEntity<ConversationDtos.PaginaMensagens> mensagens(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Instant antesDe,
            @RequestParam(required = false) UUID antesDoId,
            @RequestParam(defaultValue = "100") int limite) {
        autorizacao.exigirNoTenant(Permissao.CONVERSATIONS_READ);
        return ResponseEntity.ok(conversas.mensagensDa(
                conversationId, antesDe, antesDoId, limite));
    }

    @GetMapping("/eventos")
    ResponseEntity<ConversationDtos.PaginaEventos> eventos(
            @RequestParam(defaultValue = "0") long apos,
            @RequestParam(defaultValue = "100") int limite) {
        autorizacao.exigirNoTenant(Permissao.CONVERSATIONS_READ);
        return ResponseEntity.ok(conversas.eventosDepoisDe(apos, limite));
    }

    @PostMapping("/{conversationId}/mensagens")
    ResponseEntity<ConversationDtos.MensagemResposta> enviar(
            @PathVariable UUID conversationId,
            @Valid @RequestBody ConversationDtos.EnviarMensagemRequest requisicao,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        autorizacao.exigirNoTenant(Permissao.CONVERSATIONS_WRITE);

        // O autor sai do token, nunca do corpo. Aceitá-lo na requisição
        // permitiria a um atendente registrar mensagem em nome de outro.
        UUID autorId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                conversas.enviar(conversationId, requisicao.texto(), autorId, idempotencyKey));
    }
}
