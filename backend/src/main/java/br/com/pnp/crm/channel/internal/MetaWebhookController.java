package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.ConexaoDeCanal;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Callback autenticado da Meta para a Instagram Messaging API. */
@RestController
@RequestMapping("/api/webhooks/meta")
class MetaWebhookController {

    static final String HEADER_ASSINATURA = "X-Hub-Signature-256";

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookController.class);

    private final ChannelConnectionLookupImpl conexoes;
    private final CredenciaisDeCanal credenciais;
    private final RegistroDeEventoRecebido registro;
    private final ObjectMapper json;

    MetaWebhookController(ChannelConnectionLookupImpl conexoes,
                          CredenciaisDeCanal credenciais,
                          RegistroDeEventoRecebido registro,
                          ObjectMapper json) {
        this.conexoes = conexoes;
        this.credenciais = credenciais;
        this.registro = registro;
        this.json = json;
    }

    @GetMapping(value = "/{channelConnectionId}", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> verificar(
            @PathVariable UUID channelConnectionId,
            @RequestParam(name = "hub.mode", required = false) String modo,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String desafio) {

        ContextoDoCanal contexto = resolver(channelConnectionId);
        if (contexto == null) return ResponseEntity.notFound().build();
        if (!"subscribe".equals(modo) || desafio == null || desafio.isBlank()
                || desafio.length() > 512) {
            return ResponseEntity.badRequest().build();
        }
        boolean autentico = TenantContext.executarComo(contexto.tenantId(), () ->
                comparar(token, credenciais.recuperar(
                        channelConnectionId, TipoCredencial.META_WEBHOOK_VERIFY_TOKEN)));
        return autentico
                ? ResponseEntity.ok(desafio)
                : ResponseEntity.status(403).build();
    }

    @PostMapping(value = "/{channelConnectionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> receber(
            @PathVariable UUID channelConnectionId,
            @RequestHeader(name = HEADER_ASSINATURA, required = false) String assinatura,
            @RequestBody byte[] corpoCru) {

        ContextoDoCanal contexto = resolver(channelConnectionId);
        if (contexto == null) return ResponseEntity.notFound().build();

        boolean autentico = TenantContext.executarComo(contexto.tenantId(), () ->
                conferirAssinatura(channelConnectionId, assinatura, corpoCru));
        if (!autentico) {
            log.warn("Webhook Meta com assinatura invalida. connectionId={}", channelConnectionId);
            return ResponseEntity.status(403).build();
        }
        if (!payloadPertenceAConta(corpoCru, contexto.contaExterna())) {
            log.warn("Webhook Meta destinado a outra conta. connectionId={}", channelConnectionId);
            return ResponseEntity.status(403).build();
        }

        TenantContext.executarComo(contexto.tenantId(), () -> {
            registro.gravar(contexto.tenantId(), channelConnectionId, corpoCru);
            return null;
        });
        return ResponseEntity.ok().build();
    }

    private ContextoDoCanal resolver(UUID connectionId) {
        UUID tenantId = conexoes.resolverTenantId(connectionId).orElse(null);
        if (tenantId == null) return null;
        ConexaoDeCanal conexao = TenantContext.executarComo(tenantId,
                () -> conexoes.buscarAtiva(connectionId).orElse(null));
        if (conexao == null || conexao.tipo() != TipoCanal.INSTAGRAM
                || conexao.identificadorExterno() == null) {
            return null;
        }
        return new ContextoDoCanal(tenantId, conexao.identificadorExterno());
    }

    private boolean conferirAssinatura(UUID connectionId, String apresentada, byte[] corpoCru) {
        Optional<String> segredo = credenciais.recuperar(
                connectionId, TipoCredencial.META_APP_SECRET);
        if (segredo.isEmpty() || apresentada == null || !apresentada.startsWith("sha256=")) {
            return false;
        }
        String hexadecimal = apresentada.substring("sha256=".length());
        if (hexadecimal.length() != 64) return false;
        try {
            byte[] recebida = HexFormat.of().parseHex(hexadecimal);
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(
                    segredo.get().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(recebida, hmac.doFinal(corpoCru));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    private boolean payloadPertenceAConta(byte[] corpoCru, String contaEsperada) {
        try {
            JsonNode raiz = json.readTree(corpoCru);
            JsonNode entradas = raiz.path("entry");
            if (!"instagram".equals(raiz.path("object").asString())
                    || !entradas.isArray() || entradas.isEmpty()) {
                return false;
            }
            for (JsonNode entrada : entradas) {
                if (!contaEsperada.equals(entrada.path("id").asString())) return false;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean comparar(String apresentado, Optional<String> esperado) {
        if (apresentado == null || esperado.isEmpty()) return false;
        return MessageDigest.isEqual(
                apresentado.getBytes(StandardCharsets.UTF_8),
                esperado.get().getBytes(StandardCharsets.UTF_8));
    }

    private record ContextoDoCanal(UUID tenantId, String contaExterna) {
    }
}
