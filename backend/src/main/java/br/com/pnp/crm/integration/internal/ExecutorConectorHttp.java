package br.com.pnp.crm.integration.internal;

import br.com.pnp.crm.automation.api.AutomationAction;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
class ExecutorConectorHttp {

    private final HttpConnectorService conectores;
    private final HttpConnectorSecretService segredos;
    private final HttpConnectorAttemptService tentativas;
    private final TemplateHttpSeguro templates;
    private final TransportHttpSeguro transport;
    private final OrcamentoHttpTenant orcamento;
    private final ConcorrenciaHttpTenant concorrencia;

    ExecutorConectorHttp(HttpConnectorService conectores,
                         HttpConnectorSecretService segredos,
                         HttpConnectorAttemptService tentativas,
                         TemplateHttpSeguro templates,
                         TransportHttpSeguro transport,
                         OrcamentoHttpTenant orcamento,
                         ConcorrenciaHttpTenant concorrencia) {
        this.conectores = conectores;
        this.segredos = segredos;
        this.tentativas = tentativas;
        this.templates = templates;
        this.transport = transport;
        this.orcamento = orcamento;
        this.concorrencia = concorrencia;
    }

    AutomationAction.Resultado executar(UUID connectorId, AutomationAction.Contexto contexto) {
        HttpConnectorModel.Configuracao c = conectores.carregarAtivo(connectorId);
        if (!c.status().equals("ACTIVE")) return AutomationAction.Resultado.FALHA_PERMANENTE;

        String chave = sha256("crm-pnp:http:v1:" + contexto.tenantId() + ":"
                + contexto.executionId() + ":" + contexto.stepKey());
        Map<String, String> valores = valores(contexto, chave);
        String caminho;
        String corpo;
        URI uri;
        try {
            caminho = templates.renderizarUrl(c.caminhoTemplate(), valores);
            corpo = templates.renderizarJson(c.corpoTemplate(), valores);
            uri = URI.create(c.origem() + caminho);
        } catch (RuntimeException e) {
            return AutomationAction.Resultado.FALHA_PERMANENTE;
        }
        String targetHash = sha256(c.origem() + "|" + c.caminhoTemplate());
        HttpConnectorAttemptService.Estado tentativa = tentativas.iniciar(
                connectorId, contexto.executionId(), contexto.stepKey(),
                sha256(chave), targetHash);
        if (tentativa.status().equals("SUCCEEDED")) {
            return AutomationAction.Resultado.SUCESSO;
        }

        if (!orcamento.consumir(contexto.tenantId(), connectorId,
                c.requisicoesPorMinuto())) {
            concluirLocal(contexto, TransportHttpSeguro.Tipo.TRANSITORIA,
                    "HTTP_BUDGET_EXCEEDED");
            return AutomationAction.Resultado.FALHA_TRANSITORIA;
        }
        ConcorrenciaHttpTenant.Licenca licenca = concorrencia.tentar(contexto.tenantId());
        if (licenca == null) {
            concluirLocal(contexto, TransportHttpSeguro.Tipo.TRANSITORIA,
                    "HTTP_CONCURRENCY_LIMIT");
            return AutomationAction.Resultado.FALHA_TRANSITORIA;
        }

        try (licenca) {
            HttpConnectorSecretService.Credencial credencial =
                    segredos.resolverNoEnvio(connectorId);
            TransportHttpSeguro.Resposta resposta;
            try {
                resposta = transport.enviar(new TransportHttpSeguro.Requisicao(
                        c.metodo(), uri, corpo, c.cabecalhoIdempotencia(),
                        chave, credencial == null ? null : credencial.cabecalho(),
                        credencial == null ? null : credencial.valor(),
                        c.timeoutMs(), c.maxRespostaBytes()));
            } catch (PoliticaAntiSsrf.DestinoBloqueadoException e) {
                resposta = local(TransportHttpSeguro.Tipo.PERMANENTE,
                        "HTTP_DESTINATION_BLOCKED");
            } catch (PoliticaAntiSsrf.DestinoIndisponivelException e) {
                resposta = local(TransportHttpSeguro.Tipo.TRANSITORIA,
                        "HTTP_DNS_FAILURE");
            } catch (RuntimeException e) {
                resposta = local(TransportHttpSeguro.Tipo.TRANSITORIA,
                        "HTTP_CLIENT_FAILURE");
            }
            tentativas.concluir(contexto.executionId(), contexto.stepKey(), resposta);
            return switch (resposta.tipo()) {
                case SUCESSO -> AutomationAction.Resultado.SUCESSO;
                case TRANSITORIA -> AutomationAction.Resultado.FALHA_TRANSITORIA;
                case PERMANENTE -> AutomationAction.Resultado.FALHA_PERMANENTE;
            };
        }
    }

    private void concluirLocal(AutomationAction.Contexto contexto,
                               TransportHttpSeguro.Tipo tipo, String codigo) {
        tentativas.concluir(contexto.executionId(), contexto.stepKey(), local(tipo, codigo));
    }

    private static TransportHttpSeguro.Resposta local(
            TransportHttpSeguro.Tipo tipo, String codigo) {
        return new TransportHttpSeguro.Resposta(tipo, 0, 0, 0, codigo);
    }

    private static Map<String, String> valores(
            AutomationAction.Contexto contexto, String chave) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("tenantId", contexto.tenantId().toString());
        valores.put("executionId", contexto.executionId().toString());
        valores.put("sourceReferenceId", contexto.sourceReferenceId() == null
                ? "" : contexto.sourceReferenceId().toString());
        valores.put("stepKey", contexto.stepKey());
        valores.put("triggerType", contexto.triggerType());
        valores.put("idempotencyKey", chave);
        return Map.copyOf(valores);
    }

    private static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel.");
        }
    }
}
