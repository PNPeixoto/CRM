package br.com.pnp.crm.integration.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class HttpConnectorSecretService {

    private final JdbcTemplate jdbc;
    private final CofreSegredosHttp cofre;
    private final ValidadorConectorHttp validador;
    private final HttpConnectorService conectores;
    private final AuditTrail audit;

    HttpConnectorSecretService(JdbcTemplate jdbc, CofreSegredosHttp cofre,
                               ValidadorConectorHttp validador,
                               HttpConnectorService conectores,
                               AuditTrail audit) {
        this.jdbc = jdbc;
        this.cofre = cofre;
        this.validador = validador;
        this.conectores = conectores;
        this.audit = audit;
    }

    @Transactional
    void configurar(UUID connectorId, HttpConnectorModel.SecretRequest request,
                    UUID autorId) {
        HttpConnectorModel.Resumo conector = conectores.buscar(connectorId);
        ValidadorConectorHttp.SegredoValidado validado;
        try {
            validado = validador.validar(request);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("Credencial do conector HTTP invalida.");
        }
        if (validado.cabecalho() != null
                && validado.cabecalho().equalsIgnoreCase(
                conector.cabecalhoIdempotencia())) {
            throw new RequisicaoInvalidaException(
                    "Credencial do conector HTTP invalida.");
        }
        UUID tenantId = TenantContext.obrigatorio();
        String cifrado = cofre.cifrar(tenantId, connectorId, validado.segredo());
        jdbc.update("""
                INSERT INTO http_connector_secret (
                    id, tenant_id, connector_id, auth_type, header_name,
                    secret_ciphertext, key_version, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, connector_id) DO UPDATE SET
                    auth_type = EXCLUDED.auth_type,
                    header_name = EXCLUDED.header_name,
                    secret_ciphertext = EXCLUDED.secret_ciphertext,
                    key_version = EXCLUDED.key_version,
                    updated_by = EXCLUDED.updated_by
                """, UuidV7.gerar(), tenantId, connectorId, validado.tipo(),
                validado.cabecalho(), cifrado, CofreSegredosHttp.VERSAO_CHAVE, autorId);
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.CREDENTIAL_CHANGED,
                AuditTrail.Ator.humano(autorId),
                AuditTrail.Escopo.tenant(tenantId),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.HTTP_CONNECTOR, connectorId),
                AuditTrail.Resultado.SUCCEEDED,
                AuditTrail.Motivo.CONNECTOR_CREDENTIAL_CHANGED));
    }

    /** Chamado imediatamente antes do envio; nenhum endpoint recebe este valor. */
    @Transactional(readOnly = true)
    Credencial resolverNoEnvio(UUID connectorId) {
        UUID tenantId = TenantContext.obrigatorio();
        HttpConnectorModel.Segredo segredo = jdbc.query("""
                        SELECT auth_type, header_name, secret_ciphertext, key_version
                          FROM http_connector_secret
                         WHERE tenant_id = ? AND connector_id = ?
                        """, (rs, row) -> new HttpConnectorModel.Segredo(
                        rs.getString("auth_type"), rs.getString("header_name"),
                        rs.getString("secret_ciphertext"), rs.getInt("key_version")),
                        tenantId, connectorId)
                .stream().findFirst().orElse(null);
        if (segredo == null) return null;
        if (segredo.versaoChave() != CofreSegredosHttp.VERSAO_CHAVE) {
            throw new IllegalStateException("Versao de chave do conector HTTP indisponivel.");
        }
        String valor = cofre.decifrar(tenantId, connectorId, segredo.ciphertext());
        return segredo.tipo().equals("BEARER")
                ? new Credencial("Authorization", "Bearer " + valor)
                : new Credencial(segredo.cabecalho(), valor);
    }

    record Credencial(String cabecalho, String valor) {
    }
}
