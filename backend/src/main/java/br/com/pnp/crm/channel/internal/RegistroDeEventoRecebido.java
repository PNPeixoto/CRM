package br.com.pnp.crm.channel.internal;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Persiste os bytes do webhook antes de confirmar o recebimento. */
@Service
class RegistroDeEventoRecebido {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeEventoRecebido.class);

    private final InboundEventRepository repository;
    private final ObjectMapper json;
    private final CofreDeCredenciais cofre;
    private final EntityManager entityManager;

    RegistroDeEventoRecebido(InboundEventRepository repository, ObjectMapper json,
                             CofreDeCredenciais cofre, EntityManager entityManager) {
        this.repository = repository;
        this.json = json;
        this.cofre = cofre;
        this.entityManager = entityManager;
    }

    @Transactional
    void gravar(UUID tenantId, UUID channelConnectionId, byte[] corpoCru) {
        String hash = sha256(corpoCru);
        String corpoUtf8 = new String(corpoCru, StandardCharsets.UTF_8);
        String eventoId = extrairIdDoEvento(corpoUtf8, hash);

        // Serializa somente a mesma chave em todas as instancias. Capturar uma
        // violacao unica depois do INSERT deixaria a transacao PostgreSQL abortada.
        String chave = "inbound:" + tenantId + ':' + channelConnectionId + ':' + eventoId;
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))")
                .setParameter("key", chave)
                .getSingleResult();

        if (repository.findByTenantIdAndChannelConnectionIdAndExternalEventId(
                tenantId, channelConnectionId, eventoId).isPresent()) {
            log.debug("Evento reentregue e ignorado. connectionId={} eventoId={}",
                    channelConnectionId, eventoId);
            return;
        }

        repository.saveAndFlush(InboundEventEntity.novo(
                tenantId, channelConnectionId, eventoId,
                cofre.cifrar(corpoUtf8), hash));
    }

    private String extrairIdDoEvento(String corpoCru, String hash) {
        try {
            JsonNode raiz = json.readTree(corpoCru);
            JsonNode updateId = raiz.path("update_id");
            if (!updateId.isMissingNode() && !updateId.isNull()) {
                return updateId.asString();
            }
            JsonNode messageId = raiz.path("data").path("key").path("id");
            if (!messageId.isMissingNode() && !messageId.isNull()
                    && !messageId.asString().isBlank()) {
                return messageId.asString();
            }
        } catch (RuntimeException e) {
            // Conteudo de cliente nunca entra no log.
            log.warn("Payload de webhook nao e JSON valido; retido cifrado para diagnostico.");
        }
        // Replays malformados identicos tambem convergem.
        return "sha256:" + hash;
    }

    private String sha256(byte[] corpoCru) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(corpoCru));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel no runtime.", e);
        }
    }
}
