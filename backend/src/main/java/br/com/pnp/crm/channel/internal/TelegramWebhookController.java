package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

/**
 * Recepção de webhook do Telegram.
 *
 * <p><b>Sem autenticação de sessão, de propósito.</b> Quem chama é o Telegram,
 * não um usuário. A autenticidade vem do segredo no header, não de um token de
 * acesso — exigir sessão aqui simplesmente impediria o webhook de funcionar.
 *
 * <p><b>Ordem das operações, e onde ela difere do enunciado.</b> A regra do
 * projeto diz "responder 200 imediatamente, depois gravar o evento cru". Aqui
 * o evento é gravado <i>antes</i> do 200, e a inversão é deliberada: responder
 * primeiro significa que uma falha na gravação perde a mensagem para sempre,
 * porque o Telegram já recebeu a confirmação e não vai reenviar. Gravar é uma
 * inserção indexada, na casa de milissegundos — muito abaixo do limite que
 * levaria o provedor a reenviar. O que fica fora do request é o
 * <b>processamento</b>, que é a parte lenta e a que o enunciado quer evitar.
 */
@RestController
@RequestMapping("/api/webhooks/telegram")
class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private static final String HEADER_SEGREDO = "X-Telegram-Bot-Api-Secret-Token";

    private final ChannelConnectionLookupImpl conexoes;
    private final CredenciaisDeCanal credenciais;
    private final RegistroDeEventoRecebido registro;

    TelegramWebhookController(ChannelConnectionLookupImpl conexoes,
                              CredenciaisDeCanal credenciais,
                              RegistroDeEventoRecebido registro) {
        this.conexoes = conexoes;
        this.credenciais = credenciais;
        this.registro = registro;
    }

    /**
     * O corpo entra como {@code String} e é guardado sem reserialização. Se
     * fosse parseado e regravado, a formatação mudaria — e no dia em que o
     * WhatsApp entrar, onde a assinatura é HMAC sobre os bytes crus, o mesmo
     * descuido faria a verificação nunca bater.
     */
    @PostMapping(value = "/{channelConnectionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> receber(
            @PathVariable UUID channelConnectionId,
            @RequestHeader(name = HEADER_SEGREDO, required = false) String segredoApresentado,
            @RequestBody byte[] corpoCru) {

        UUID tenantId = conexoes.resolverTenantId(channelConnectionId).orElse(null);
        if (tenantId == null) {
            // 404 e não 403: para quem sonda, conexão inexistente e conexão de
            // outro cliente precisam ser indistinguíveis.
            return ResponseEntity.notFound().build();
        }

        boolean autentico = TenantContext.executarComo(tenantId,
                () -> conferirSegredo(channelConnectionId, segredoApresentado));

        if (!autentico) {
            log.warn("Webhook do Telegram com segredo inválido. connectionId={}", channelConnectionId);
            return ResponseEntity.status(403).build();
        }

        TenantContext.executarComo(tenantId, () -> {
            registro.gravar(tenantId, channelConnectionId, corpoCru);
            return null;
        });

        // 200 sem corpo. O Telegram não espera conteúdo, e devolver algo só
        // aumentaria a superfície.
        return ResponseEntity.ok().build();
    }

    /**
     * Comparação em <b>tempo constante</b>.
     *
     * <p>{@code String.equals} sai no primeiro byte diferente, e a diferença de
     * tempo entre "errou no primeiro caractere" e "errou no último" é medível
     * pela rede. Com ela, o segredo pode ser descoberto caractere a caractere,
     * em algumas centenas de requisições — em vez das tentativas que a
     * entropia dele deveria exigir.
     */
    private boolean conferirSegredo(UUID channelConnectionId, String apresentado) {
        Optional<String> esperado =
                credenciais.recuperar(channelConnectionId, TipoCredencial.TELEGRAM_WEBHOOK_SECRET);

        if (esperado.isEmpty()) {
            // Conexão sem segredo configurado não aceita webhook. Falha
            // fechada: o contrário permitiria a qualquer um postar eventos em
            // uma conexão recém-criada.
            log.warn("Webhook recebido em conexão sem segredo configurado. connectionId={}",
                    channelConnectionId);
            return false;
        }
        if (apresentado == null) {
            return false;
        }

        return MessageDigest.isEqual(
                apresentado.getBytes(StandardCharsets.UTF_8),
                esperado.get().getBytes(StandardCharsets.UTF_8));
    }
}
