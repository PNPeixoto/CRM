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
     * Extrai o tenant de um destino, para conferir contra o token de quem se
     * inscreve.
     *
     * @return vazio quando o destino não segue o padrão — e destino fora do
     * padrão é recusado, nunca liberado por omissão
     */
    public static Optional<UUID> tenantDoDestino(String destino) {
        if (destino == null || !destino.startsWith(PREFIXO_TENANT)) {
            return Optional.empty();
        }
        String resto = destino.substring(PREFIXO_TENANT.length());
        int fim = resto.indexOf('/');
        String candidato = fim < 0 ? resto : resto.substring(0, fim);
        try {
            return Optional.of(UUID.fromString(candidato));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
