package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/** Webhook autenticado da ponte Evolution, habilitado somente por configuracao. */
@RestController
@RequestMapping("/api/webhooks/evolution")
@ConditionalOnProperty(name = "app.providers.evolution.enabled", havingValue = "true")
class EvolutionWebhookController {

    static final String HEADER_SEGREDO = "X-Crm-Pnp-Evolution-Secret";
    private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookController.class);

    private final ChannelConnectionLookupImpl conexoes;
    private final CredenciaisDeCanal credenciais;
    private final RegistroDeEventoRecebido registro;

    EvolutionWebhookController(ChannelConnectionLookupImpl conexoes,
                               CredenciaisDeCanal credenciais,
                               RegistroDeEventoRecebido registro) {
        this.conexoes = conexoes;
        this.credenciais = credenciais;
        this.registro = registro;
    }

    @PostMapping(value = "/{channelConnectionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> receber(
            @PathVariable UUID channelConnectionId,
            @RequestHeader(name = HEADER_SEGREDO, required = false) String segredoApresentado,
            @RequestBody byte[] corpoCru) {
        UUID tenantId = conexoes.resolverTenantId(channelConnectionId).orElse(null);
        if (tenantId == null) return ResponseEntity.notFound().build();

        boolean autentico = TenantContext.executarComo(tenantId,
                () -> conferirSegredo(channelConnectionId, segredoApresentado));
        if (!autentico) {
            log.warn("Webhook Evolution com segredo invalido. connectionId={}", channelConnectionId);
            return ResponseEntity.status(403).build();
        }

        TenantContext.executarComo(tenantId, () -> {
            registro.gravar(tenantId, channelConnectionId, corpoCru);
            return null;
        });
        return ResponseEntity.ok().build();
    }

    private boolean conferirSegredo(UUID connectionId, String apresentado) {
        Optional<String> esperado = credenciais.recuperar(
                connectionId, TipoCredencial.EVOLUTION_WEBHOOK_SECRET);
        return esperado.isPresent() && apresentado != null && MessageDigest.isEqual(
                apresentado.getBytes(StandardCharsets.UTF_8),
                esperado.get().getBytes(StandardCharsets.UTF_8));
    }
}
