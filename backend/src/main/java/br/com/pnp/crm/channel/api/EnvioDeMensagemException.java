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
    private final java.time.Duration tentarNovamenteEm;

    private EnvioDeMensagemException(String motivoSanitizado, boolean permanente,
                                     java.time.Duration tentarNovamenteEm) {
        super("ENVIO_DE_MENSAGEM_FALHOU", motivoSanitizado);
        this.permanente = permanente;
        this.tentarNovamenteEm = tentarNovamenteEm;
    }

    /**
     * Falha que não melhora com repetição: mensagem malformada, destinatário
     * inválido, credencial recusada. O worker manda direto para a fila morta —
     * retentar só gastaria cota do provedor e atrasaria a fila.
     */
    public static EnvioDeMensagemException permanente(String motivoSanitizado) {
        return new EnvioDeMensagemException(motivoSanitizado, true, null);
    }

    /**
     * Falha que pode passar: timeout, 5xx, limite de taxa. O worker retenta
     * com backoff exponencial.
     */
    public static EnvioDeMensagemException temporaria(String motivoSanitizado) {
        return new EnvioDeMensagemException(motivoSanitizado, false, null);
    }

    /** Falha temporaria com espera exigida pelo provedor (por exemplo, 429). */
    public static EnvioDeMensagemException temporaria(
            String motivoSanitizado, java.time.Duration tentarNovamenteEm) {
        if (tentarNovamenteEm == null || tentarNovamenteEm.isNegative()
                || tentarNovamenteEm.isZero()) {
            return temporaria(motivoSanitizado);
        }
        return new EnvioDeMensagemException(motivoSanitizado, false, tentarNovamenteEm);
    }

    public boolean isPermanente() {
        return permanente;
    }

    public java.util.Optional<java.time.Duration> tentarNovamenteEm() {
        return java.util.Optional.ofNullable(tentarNovamenteEm);
    }
}
