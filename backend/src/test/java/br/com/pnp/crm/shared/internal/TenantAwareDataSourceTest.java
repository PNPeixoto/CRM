package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAwareDataSourceTest {

    @AfterEach
    void limparContexto() {
        TenantContext.limpar();
    }

    @Test
    void usaEscopoLocalAoAbrirTransacaoEZeraSessaoAoFechar() throws Exception {
        UUID tenantId = UUID.fromString("019fc000-0000-7000-8000-000000000001");
        DataSource alvo = mock(DataSource.class);
        Connection fisica = mock(Connection.class);
        PreparedStatement sessao = mock(PreparedStatement.class);
        PreparedStatement transacao = mock(PreparedStatement.class);
        PreparedStatement limpeza = mock(PreparedStatement.class);

        when(alvo.getConnection()).thenReturn(fisica);
        when(fisica.prepareStatement("SELECT set_config('app.tenant_id', ?, ?)") )
                .thenReturn(sessao, transacao, limpeza);
        when(fisica.isClosed()).thenReturn(false);
        when(fisica.getAutoCommit()).thenReturn(true);

        TenantContext.definir(tenantId);
        Connection conexao = new TenantAwareDataSource(alvo).getConnection();
        conexao.setAutoCommit(false);
        conexao.close();

        verify(sessao).setString(1, tenantId.toString());
        verify(sessao).setBoolean(2, false);
        verify(transacao).setString(1, tenantId.toString());
        verify(transacao).setBoolean(2, true);
        verify(limpeza).setString(1, "");
        verify(limpeza).setBoolean(2, false);
        verify(fisica).close();
    }
}
