package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
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
 */
class TenantAwareDataSource extends DelegatingDataSource {

    // set_config aceita parâmetro; o comando SET não aceita. Como o valor vem
    // de UUID já tipado o risco de injeção é nulo, mas a regra do projeto é
    // query sempre parametrizada, e a exceção de hoje é o descuido de amanhã.
    private static final String DEFINIR_TENANT = "SELECT set_config('app.tenant_id', ?, false)";

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
        try (PreparedStatement statement = connection.prepareStatement(DEFINIR_TENANT)) {
            statement.setString(1, tenantId);
            statement.execute();
        } catch (SQLException e) {
            // Devolver a conexão ao pool é obrigatório: sem isto, uma falha
            // recorrente aqui esgota o pool e derruba a aplicação inteira por
            // um erro que era de uma requisição só.
            connection.close();
            throw e;
        }
        return connection;
    }
}
