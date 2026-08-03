package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Aplica {@code app.tenant_id} em toda conexão entregue à aplicação, que é o
 * que faz as políticas de Row Level Security da migration V1 valerem.
 *
 * <p><b>Por que aqui e não num aspecto sobre {@code @Transactional}:</b> um
 * aspecto só cobre o que passa por ele. Uma consulta feita fora de transação,
 * um {@code JdbcTemplate} usado direto, um repositório novo que alguém
 * esqueceu de anotar — todos escapariam. O ponto por onde <em>tudo</em> passa
 * obrigatoriamente é a obtenção da conexão. Fechando aqui, não existe caminho
 * até o Postgres sem o tenant definido.
 *
 * <p><b>Por que o set é incondicional:</b> quando não há tenant no contexto,
 * a variável é definida como string vazia, e não deixada como está. A função
 * {@code current_tenant_id()} converte vazio em NULL, e toda política de RLS
 * passa a não casar com nenhuma linha. O resultado é que uma conexão sem
 * tenant enxerga zero registros em vez de enxergar os do usuário anterior que
 * usou aquela conexão do pool. Falha fechada, e sem depender de ninguém
 * lembrar de limpar nada.
 *
 * <p>A configuração de sessão aplicada no checkout é apenas a rede de proteção
 * para consultas JDBC em autocommit. Quando o Spring abre uma transação, a
 * conexão reaplica o mesmo valor com escopo local à transação; ao devolver a
 * conexão ao pool, o valor de sessão é zerado explicitamente.
 */
class TenantAwareDataSource extends DelegatingDataSource {

    // set_config aceita parâmetro; o comando SET não aceita. Como o valor vem
    // de UUID já tipado o risco de injeção é nulo, mas a regra do projeto é
    // query sempre parametrizada, e a exceção de hoje é o descuido de amanhã.
    private static final String DEFINIR_TENANT = "SELECT set_config('app.tenant_id', ?, ?)";

    private static final String SEM_TENANT = "";

    TenantAwareDataSource(DataSource alvo) {
        super(alvo);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return definirTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return definirTenant(super.getConnection(username, password));
    }

    private Connection definirTenant(Connection connection) throws SQLException {
        String tenantId = TenantContext.atual().map(UUID::toString).orElse(SEM_TENANT);
        definirTenant(connection, tenantId, false);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new TenantScopedConnection(connection, tenantId));
    }

    private static void definirTenant(Connection connection, String tenantId,
                                      boolean localNaTransacao) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DEFINIR_TENANT)) {
            statement.setString(1, tenantId);
            statement.setBoolean(2, localNaTransacao);
            statement.execute();
        }
    }

    private static final class TenantScopedConnection implements InvocationHandler {

        private final Connection alvo;
        private final String tenantId;
        private boolean fechada;

        private TenantScopedConnection(Connection alvo, String tenantId) {
            this.alvo = alvo;
            this.tenantId = tenantId;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] argumentos) throws Throwable {
            String nome = metodo.getName();
            if ("close".equals(nome)) {
                fechar();
                return null;
            }
            if ("isClosed".equals(nome) && fechada) {
                return true;
            }
            if ("unwrap".equals(nome) && ((Class<?>) argumentos[0]).isInstance(proxy)) {
                return proxy;
            }
            if ("isWrapperFor".equals(nome) && ((Class<?>) argumentos[0]).isInstance(proxy)) {
                return true;
            }

            try {
                Object resultado = metodo.invoke(alvo, argumentos);
                if ("setAutoCommit".equals(nome) && Boolean.FALSE.equals(argumentos[0])) {
                    definirTenant(alvo, tenantId, true);
                } else if (("commit".equals(nome)
                        || ("rollback".equals(nome) && metodo.getParameterCount() == 0))
                        && !alvo.getAutoCommit()) {
                    definirTenant(alvo, tenantId, true);
                }
                return resultado;
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private void fechar() throws SQLException {
            if (fechada) {
                return;
            }
            fechada = true;
            SQLException falha = null;
            try {
                if (!alvo.isClosed()) {
                    if (!alvo.getAutoCommit()) {
                        alvo.rollback();
                        alvo.setAutoCommit(true);
                    }
                    definirTenant(alvo, SEM_TENANT, false);
                }
            } catch (SQLException exception) {
                falha = exception;
            } finally {
                try {
                    alvo.close();
                } catch (SQLException exception) {
                    if (falha == null) {
                        falha = exception;
                    } else {
                        falha.addSuppressed(exception);
                    }
                }
            }
            if (falha != null) {
                throw falha;
            }
        }
    }
}
