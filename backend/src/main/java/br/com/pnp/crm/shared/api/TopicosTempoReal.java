package br.com.pnp.crm.shared.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Nomes dos destinos STOMP, em um só lugar.
 *
 * <p>Centralizado porque duas partes distantes precisam concordar: quem
 * publica e quem <b>autoriza a inscrição</b>. Se o formato do destino for
 * montado com concatenação solta nos dois lados, basta um deles mudar para a
 * autorização passar a validar um padrão que ninguém mais usa — e ela
 * continuaria "passando", sem proteger nada.
 *
 * <p>O {@code tenantId} aparece no destino de propósito. Ele não é segredo
 * (quem está autenticado já conhece o próprio) e tê-lo no caminho permite
 * rejeitar a inscrição olhando só o texto do destino, sem consultar o banco a
 * cada SUBSCRIBE.
 */
public final class TopicosTempoReal {

    private static final String PREFIXO_TENANT = "/topic/tenant/";
    private static final String SEGMENTO_CONVERSA = "/conversa/";
    private static final String SUFIXO_INBOX = "/inbox";

    private TopicosTempoReal() {
    }

    /** Atividade da caixa de entrada do tenant: conversa nova, conversa que subiu. */
    public static String inbox(UUID tenantId) {
        return PREFIXO_TENANT + tenantId + SUFIXO_INBOX;
    }

    /** Mensagens de uma conversa específica. */
    public static String conversa(UUID tenantId, UUID conversationId) {
        return PREFIXO_TENANT + tenantId + SEGMENTO_CONVERSA + conversationId;
    }

    /**
     * Analisa somente destinos que o backend publica hoje.
     *
     * <p>Validar apenas o prefixo e o tenant deixaria qualquer destino futuro
     * sob {@code /topic/tenant/{id}/...} autorizado por padrão. Segurança de
     * SUBSCRIBE usa allowlist: inbox e conversa são aceitos; todo o resto
     * falha fechado até ganhar decisão e teste próprios.
     */
    public static Optional<Destino> analisarDestino(String destino) {
        if (destino == null || !destino.startsWith(PREFIXO_TENANT)) {
            return Optional.empty();
        }

        String[] segmentos = destino.substring(PREFIXO_TENANT.length()).split("/", -1);
        try {
            UUID tenantId = UUID.fromString(segmentos[0]);
            if (segmentos.length == 2 && "inbox".equals(segmentos[1])) {
                return Optional.of(new Destino(tenantId, TipoDestino.INBOX, null));
            }
            if (segmentos.length == 3 && "conversa".equals(segmentos[1])) {
                UUID conversationId = UUID.fromString(segmentos[2]);
                return Optional.of(new Destino(tenantId, TipoDestino.CONVERSA, conversationId));
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Extrai o tenant de um destino, para conferir contra o token de quem se
     * inscreve.
     *
     * @return vazio quando o destino não segue o padrão — e destino fora do
     * padrão é recusado, nunca liberado por omissão
     */
    public static Optional<UUID> tenantDoDestino(String destino) {
        return analisarDestino(destino).map(Destino::tenantId);
    }

    public record Destino(UUID tenantId, TipoDestino tipo, UUID conversationId) {
    }

    public enum TipoDestino {
        INBOX,
        CONVERSA
    }
}
