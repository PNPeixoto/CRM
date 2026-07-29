package br.com.pnp.crm.shared.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant da requisição em curso.
 *
 * <p>O valor é sempre derivado de fonte confiável — token verificado no filtro
 * de autenticação, ou resolução por slug durante o login. Nunca de campo do
 * corpo, parâmetro de query ou cabeçalho controlado pelo cliente.
 *
 * <p>Usa {@link ThreadLocal} porque o modelo de execução é uma thread por
 * requisição. Funciona igual com threads virtuais, que têm ThreadLocal
 * próprio. O preço é a obrigação de limpar no {@code finally} — sem isso a
 * thread volta ao pool carregando o tenant do usuário anterior, que é
 * exatamente o vazamento que o RLS existe para impedir.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> ATUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void definir(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId não pode ser nulo");
        }
        ATUAL.set(tenantId);
    }

    /**
     * @return o tenant corrente, ou vazio nas requisições anteriores à
     * autenticação (login, webhook, health check).
     */
    public static Optional<UUID> atual() {
        return Optional.ofNullable(ATUAL.get());
    }

    /**
     * @throws TenantNaoDefinidoException quando não há tenant no contexto. Use
     * onde a ausência é bug de programação, não estado legítimo.
     */
    public static UUID obrigatorio() {
        UUID tenantId = ATUAL.get();
        if (tenantId == null) {
            throw new TenantNaoDefinidoException();
        }
        return tenantId;
    }

    public static void limpar() {
        ATUAL.remove();
    }

    /**
     * Executa a ação com o tenant informado e restaura o valor anterior ao
     * final. Necessário no login, que resolve o tenant antes de existir
     * autenticação, e em tarefas de fundo que percorrem vários tenants.
     */
    public static <T> T executarComo(UUID tenantId, java.util.function.Supplier<T> acao) {
        UUID anterior = ATUAL.get();
        definir(tenantId);
        try {
            return acao.get();
        } finally {
            if (anterior == null) {
                ATUAL.remove();
            } else {
                ATUAL.set(anterior);
            }
        }
    }
}
