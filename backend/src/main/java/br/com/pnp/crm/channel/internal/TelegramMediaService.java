package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

/** Baixa a copia oficial e a mantem inacessivel ate existir scanner aprovado. */
@Service
class TelegramMediaService {

    private final ObjectMapper json;
    private final TelegramAdapter adapter;
    private final QuarentenaDeMidia quarentena;
    private final EntityManager entityManager;

    TelegramMediaService(ObjectMapper json, TelegramAdapter adapter,
                         QuarentenaDeMidia quarentena, EntityManager entityManager) {
        this.json = json;
        this.adapter = adapter;
        this.quarentena = quarentena;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<UUID> ingerir(UUID channelConnectionId, String payloadCru) {
        Optional<Referencia> referencia = extrair(payloadCru);
        if (referencia.isEmpty()) return Optional.empty();
        Referencia ref = referencia.get();
        if (ref.tamanhoDeclarado() > QuarentenaDeMidia.LIMITE_BYTES) {
            throw EnvioDeMensagemException.permanente("Midia excede o limite de 20 MB.");
        }

        UUID tenantId = TenantContext.obrigatorio();
        String chaveLock = "telegram-media:" + tenantId + ':' + channelConnectionId + ':' + ref.fileId();
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))")
                .setParameter("key", chaveLock).getSingleResult();

        Optional<UUID> existente = entityManager.createNativeQuery("""
                SELECT id FROM channel_media
                 WHERE channel_connection_id = :connectionId
                   AND external_file_id = :fileId AND deleted_at IS NULL
                """, UUID.class)
                .setParameter("connectionId", channelConnectionId)
                .setParameter("fileId", ref.fileId())
                .getResultStream().findFirst();
        if (existente.isPresent()) return existente;

        TelegramAdapter.ArquivoRemoto remoto = adapter.prepararDownload(
                channelConnectionId, ref.fileId());
        if (remoto.tamanhoDeclarado() > QuarentenaDeMidia.LIMITE_BYTES) {
            throw EnvioDeMensagemException.permanente("Midia excede o limite de 20 MB.");
        }

        UUID mediaId = UuidV7.gerar();
        try (TelegramTransport.Download download = adapter.abrirDownload(remoto)) {
            if (download.status() < 200 || download.status() >= 300) {
                throw EnvioDeMensagemException.temporaria(
                        "Provedor recusou o download da midia.");
            }
            long contentLength = numero(download.primeiroHeader("Content-Length"));
            if (contentLength > QuarentenaDeMidia.LIMITE_BYTES) {
                throw EnvioDeMensagemException.permanente("Midia excede o limite de 20 MB.");
            }
            var armazenada = quarentena.armazenar(tenantId, mediaId, download.corpo());
            removerArquivoSeTransacaoReverter(armazenada.storageKey());
            entityManager.createNativeQuery("""
                    INSERT INTO channel_media (
                        id, tenant_id, channel_connection_id, external_file_id,
                        storage_key, declared_mime_type, detected_mime_type,
                        byte_size, sha256, status
                    ) VALUES (
                        :id, :tenant, :connection, :external, :storage,
                        :declared, :detected, :size, :sha, 'QUARANTINED'
                    )
                    """)
                    .setParameter("id", mediaId)
                    .setParameter("tenant", tenantId)
                    .setParameter("connection", channelConnectionId)
                    .setParameter("external", ref.fileId())
                    .setParameter("storage", armazenada.storageKey())
                    .setParameter("declared", ref.mimeDeclarado())
                    .setParameter("detected", armazenada.tipoDetectado())
                    .setParameter("size", armazenada.tamanho())
                    .setParameter("sha", armazenada.sha256())
                    .executeUpdate();
            return Optional.of(mediaId);
        } catch (java.io.IOException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Falha ao fechar download de midia.");
        }
    }

    private void removerArquivoSeTransacaoReverter(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                quarentena.remover(storageKey);
                            } catch (RuntimeException ignored) {
                                // A falha original prevalece; a raiz privada pode ser reconciliada.
                            }
                        }
                    }
                });
    }

    private Optional<Referencia> extrair(String payloadCru) {
        JsonNode mensagem = json.readTree(payloadCru).path("message");
        JsonNode midia = null;
        if (mensagem.path("document").isObject()) midia = mensagem.path("document");
        else if (mensagem.path("audio").isObject()) midia = mensagem.path("audio");
        else if (mensagem.path("voice").isObject()) midia = mensagem.path("voice");
        else if (mensagem.path("video").isObject()) midia = mensagem.path("video");
        else if (mensagem.path("photo").isArray() && !mensagem.path("photo").isEmpty()) {
            midia = mensagem.path("photo").get(mensagem.path("photo").size() - 1);
        }
        if (midia == null || midia.path("file_id").asString("").isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Referencia(
                midia.path("file_id").asString(),
                midia.path("mime_type").asString(null),
                midia.path("file_size").asLong(-1)));
    }

    private long numero(String valor) {
        try {
            return valor == null ? -1 : Long.parseLong(valor);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    record Referencia(String fileId, String mimeDeclarado, long tamanhoDeclarado) {
    }
}
