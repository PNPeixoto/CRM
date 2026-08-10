package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Pareamento pedido para um canal que nao pode ser pareado agora.
 *
 * <p>Cobre tres situacoes distintas com um codigo so, de proposito: canal de
 * outro tipo, canal inativo e adaptador desabilitado no servidor. Nenhuma
 * delas e falha do usuario nem revela algo util a quem sonda — e todas se
 * resolvem lendo a mensagem, que descreve a acao.
 */
final class PareamentoIndisponivelException extends DomainException {

    PareamentoIndisponivelException(String mensagem) {
        super("PAREAMENTO_INDISPONIVEL", mensagem);
    }
}
