package br.com.pnp.crm.channel.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Grava o evento cru vindo do webhook.
 *
 * <p>Faz o mínimo possível dentro do request: extrai o identificador do evento
 * e persiste. Toda interpretação — descobrir se é mensagem, de quem, com que
 * conteúdo — acontece depois, no worker. Interpretar aqui é o erro clássico:
 * um payload inesperado derrubaria o request, o provedor não receberia 200,
 * reenviaria, e depois de algumas falhas desativaria o webhook.
 */
@Service
class RegistroDeEventoRecebido {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeEventoRecebido.class);

    private final InboundEventRepository repository;
    private final ObjectMapper json;

    RegistroDeEventoRecebido(InboundEventRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional
    void gravar(UUID tenantId, UUID channelConnectionId, String corpoCru) {
        String eventoId = extrairUpdateId(corpoCru);

        try {
            repository.saveAndFlush(InboundEventEntity.novo(
                    tenantId, channelConnectionId, eventoId, corpoCru));
        } catch (DataIntegrityViolationException e) {
            // Reentrega do mesmo update. Barrada pela constraint, tratada como
            // sucesso: o provedor precisa do 200, senão continua reenviando.
            log.debug("Evento reentregue e ignorado. connectionId={} eventoId={}",
                    channelConnectionId, eventoId);
        }
    }

    /**
     * O {@code update_id} é o identificador de idempotência do Telegram.
     *
     * <p>Quando ausente — payload malformado ou tipo de evento inesperado — o
     * evento ainda é gravado, com um identificador sintético. Descartar
     * significaria perder justamente o caso que precisa ser investigado, e é
     * para isso que a tabela de entrada existe.
     */
    private String extrairUpdateId(String corpoCru) {
        try {
            JsonNode raiz = json.readTree(corpoCru);
            JsonNode updateId = raiz.path("update_id");
            if (!updateId.isMissingNode() && !updateId.isNull()) {
                return updateId.asString();
            }
        } catch (RuntimeException e) {
            log.warn("Payload de webhook não é JSON válido; gravado assim mesmo para diagnóstico.");
        }
        return "sem-update-id:" + UUID.randomUUID();
    }
}
