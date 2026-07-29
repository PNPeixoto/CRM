package br.com.pnp.crm.channel.api;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * O provedor recusou a mensagem ou não respondeu.
 *
 * <p>A mensagem desta exceção vai para o log e para {@code
 * message.failure_reason}, então ela precisa estar <b>sanitizada</b> antes de
 * chegar aqui: resposta de API externa pode ecoar o token que enviamos, e
 * gravar isso no banco transformaria a tabela de mensagens em um depósito de
 * credencial.
 */
public class EnvioDeMensagemException extends DomainException {

    private final boolean permanente;

    private EnvioDeMensagemException(String motivoSanitizado, boolean permanente) {
        super("ENVIO_DE_MENSAGEM_FALHOU", motivoSanitizado);
        this.permanente = permanente;
    }

    /**
     * Falha que não melhora com repetição: mensagem malformada, destinatário
     * inválido, credencial recusada. O worker manda direto para a fila morta —
     * retentar só gastaria cota do provedor e atrasaria a fila.
     */
    public static EnvioDeMensagemException permanente(String motivoSanitizado) {
        return new EnvioDeMensagemException(motivoSanitizado, true);
    }

    /**
     * Falha que pode passar: timeout, 5xx, limite de taxa. O worker retenta
     * com backoff exponencial.
     */
    public static EnvioDeMensagemException temporaria(String motivoSanitizado) {
        return new EnvioDeMensagemException(motivoSanitizado, false);
    }

    public boolean isPermanente() {
        return permanente;
    }
}
