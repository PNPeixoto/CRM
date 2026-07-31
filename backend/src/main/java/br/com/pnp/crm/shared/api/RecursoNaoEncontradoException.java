package br.com.pnp.crm.shared.api;

/**
 * Registro inexistente <b>ou de outro tenant</b> — os dois casos produzem esta
 * mesma exceção.
 *
 * <p>Distinguir "não existe" de "existe mas não é seu" confirmaria a
 * existência de dados de outro cliente para quem chuta identificadores. O RLS
 * já faz a consulta voltar vazia nos dois casos; esta exceção preserva a
 * indistinção até a resposta HTTP.
 */
public class RecursoNaoEncontradoException extends DomainException {

    public RecursoNaoEncontradoException(String recurso) {
        super("RECURSO_NAO_ENCONTRADO", recurso + " não encontrado.");
    }
}
