package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Papel de sistema nao se edita nem se apaga.
 *
 * <p>{@code OWNER} e a saida de emergencia do tenant: e o papel que garante que
 * sempre existe alguem capaz de reconstruir os demais. Permitir edita-lo
 * transformaria um erro de configuracao em perda de acesso irreversivel sem
 * intervencao no banco.
 */
final class PapelDeSistemaException extends DomainException {

    PapelDeSistemaException() {
        super("PAPEL_DE_SISTEMA_IMUTAVEL",
                "Papel de sistema não pode ser alterado nem removido.");
    }
}
