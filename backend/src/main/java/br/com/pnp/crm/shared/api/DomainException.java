package br.com.pnp.crm.shared.api;

/**
 * Base de toda exceção de regra de negócio.
 *
 * <p>Existe para que o handler global traduza domínio em HTTP num só lugar.
 * Lançar {@code RuntimeException} obrigaria o handler a decidir pelo texto da
 * mensagem, e mensagem é para humano, não para controle de fluxo.
 *
 * <p>O {@code codigo} é estável e legível por máquina — o frontend decide o
 * que fazer por ele, nunca pelo texto, que é traduzível e muda.
 */
public abstract class DomainException extends RuntimeException {

    private final String codigo;

    protected DomainException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
