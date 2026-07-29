package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Conversa inexistente <b>ou de outro tenant</b> — os dois casos produzem esta
 * mesma exceção, e é assim que precisa ser.
 *
 * <p>Distinguir "não existe" de "existe mas não é sua" confirmaria a
 * existência de dados de outro cliente para quem chuta identificadores. O RLS
 * já faz a consulta voltar vazia nos dois casos; esta exceção só preserva essa
 * indistinção até a resposta HTTP.
 */
class ConversaNaoEncontradaException extends DomainException {

    ConversaNaoEncontradaException() {
        super("CONVERSA_NAO_ENCONTRADA", "Conversa não encontrada.");
    }
}
