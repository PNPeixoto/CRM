package br.com.pnp.crm.privacy.internal;

import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Atende pedidos de acesso e de eliminacao do titular.
 *
 * <p>Usa consulta nativa em vez de repositorios de outros modulos: o modulo de
 * privacidade atravessa contato, conversa, mensagem, oportunidade e tarefa, e
 * injetar cinco repositorios internos quebraria a fronteira que o
 * {@code FronteiraDeModulosTest} protege. O RLS continua valendo — toda
 * consulta roda sob o tenant do contexto.
 */
@Service
class DireitosDoTitularService {

    private static final String MASCARA_INTERNA = "(atendente da empresa)";

    private final EntityManager entityManager;

    DireitosDoTitularService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    ExportacaoDeTitular exportar(UUID contatoId, UUID solicitante, Instant agora) {
        Map<String, String> contato = contato(contatoId);

        List<ExportacaoDeTitular.Secao> secoes = new ArrayList<>();
        secoes.add(new ExportacaoDeTitular.Secao(
                "Identificação do contato", "contact",
                "Cadastro para atendimento e relacionamento comercial",
                prazo("contatos-excluidos"),
                1, List.of(contato)));

        List<Map<String, String>> conversas = conversas(contatoId);
        secoes.add(new ExportacaoDeTitular.Secao(
                "Conversas", "conversation",
                "Registro do atendimento prestado",
                prazo("conversas-encerradas"),
                conversas.size(), conversas));

        List<Map<String, String>> mensagens = mensagens(contatoId);
        secoes.add(new ExportacaoDeTitular.Secao(
                "Mensagens", "message",
                "Conteúdo trocado no atendimento",
                prazo("conversas-encerradas"),
                mensagens.size(), mensagens));

        List<Map<String, String>> oportunidades = oportunidades(contatoId);
        secoes.add(new ExportacaoDeTitular.Secao(
                "Oportunidades", "deal",
                "Acompanhamento comercial vinculado ao contato",
                "sem prazo definido — ver LGPD-002",
                oportunidades.size(), oportunidades));

        List<Map<String, String>> tarefas = tarefas(contatoId);
        secoes.add(new ExportacaoDeTitular.Secao(
                "Tarefas", "task",
                "Compromissos internos referentes ao contato",
                "sem prazo definido — ver LGPD-002",
                tarefas.size(), tarefas));

        List<String> observacoes = new ArrayList<>();
        observacoes.add("A identidade de atendentes aparece como função: eles são"
                + " outros titulares e seus dados não pertencem a este pedido.");
        observacoes.add("Mensagens enviadas a provedores de canal — Telegram, WhatsApp —"
                + " também são tratadas por eles, sob políticas próprias.");
        if (temHold(contatoId, agora)) {
            observacoes.add("Há retenção legal declarada sobre este titular:"
                    + " a eliminação está bloqueada enquanto ela vigorar.");
        }

        return new ExportacaoDeTitular(contatoId, agora, solicitante, secoes, observacoes);
    }

    /**
     * Elimina o dado pessoal do titular, ou recusa com motivo.
     *
     * <p>Nunca apaga a linha: oportunidade, tarefa e conversa a referenciam, e
     * remove-la destruiria histórico comercial que pertence à empresa, não ao
     * titular. A eliminação acontece por substituição do dado pessoal.
     *
     * @throws EliminacaoRecusadaException quando há retenção legal vigente —
     *                                     recusa fundamentada, nunca silêncio
     */
    @Transactional
    Instant anonimizar(UUID contatoId, Instant agora) {
        contato(contatoId);

        if (temHold(contatoId, agora)) {
            throw new EliminacaoRecusadaException(
                    "Há retenção legal declarada sobre este titular. A eliminação fica"
                    + " bloqueada até que ela seja encerrada, e o pedido não foi perdido:"
                    + " repita-o depois do fim da retenção.");
        }

        int alterados = entityManager.createNativeQuery("""
                UPDATE contact
                   SET name          = 'Titular anonimizado',
                       email         = NULL,
                       phone         = NULL,
                       company_name  = NULL,
                       notes         = NULL,
                       anonymized_at = :agora,
                       updated_at    = :agora
                 WHERE tenant_id = :tenant AND id = :id AND anonymized_at IS NULL
                """)
                .setParameter("agora", Timestamp.from(agora))
                .setParameter("tenant", TenantContext.obrigatorio())
                .setParameter("id", contatoId)
                .executeUpdate();

        if (alterados == 0) {
            // Já anonimizado antes. Repetir o pedido é idempotente e não é erro:
            // o titular pediu que o dado sumisse, e ele sumiu.
            return agora;
        }
        return agora;
    }

    // -----------------------------------------------------------------------

    private boolean temHold(UUID contatoId, Instant agora) {
        return (Boolean) entityManager
                .createNativeQuery("SELECT ha_legal_hold('CONTACT', :id, :agora)")
                .setParameter("id", contatoId)
                .setParameter("agora", Timestamp.from(agora))
                .getSingleResult();
    }

    private Map<String, String> contato(UUID contatoId) {
        List<?> linhas = entityManager.createNativeQuery("""
                SELECT name, email, phone, company_name, notes, contact_kind,
                       created_at, anonymized_at
                  FROM contact
                 WHERE tenant_id = :tenant AND id = :id
                """)
                .setParameter("tenant", TenantContext.obrigatorio())
                .setParameter("id", contatoId)
                .getResultList();

        if (linhas.isEmpty()) {
            throw new RecursoNaoEncontradoException("Contato");
        }
        Object[] c = (Object[]) linhas.getFirst();
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("nome", texto(c[0]));
        campos.put("email", texto(c[1]));
        campos.put("telefone", texto(c[2]));
        campos.put("empresa", texto(c[3]));
        campos.put("observacoes", texto(c[4]));
        campos.put("tipo", texto(c[5]));
        campos.put("cadastradoEm", texto(c[6]));
        campos.put("anonimizadoEm", texto(c[7]));
        return campos;
    }

    private List<Map<String, String>> conversas(UUID contatoId) {
        return mapear(entityManager.createNativeQuery("""
                SELECT c.id, c.status, c.created_at, c.last_message_at, cc.kind
                  FROM conversation c
                  JOIN channel_connection cc
                    ON cc.tenant_id = c.tenant_id AND cc.id = c.channel_connection_id
                 WHERE c.tenant_id = :tenant AND c.contact_id = :id
                 ORDER BY c.created_at
                """)
                .setParameter("tenant", TenantContext.obrigatorio())
                .setParameter("id", contatoId)
                .getResultList(),
                List.of("conversaId", "situacao", "abertaEm", "ultimaMensagemEm", "canal"));
    }

    private List<Map<String, String>> mensagens(UUID contatoId) {
        // `author_user_id` nao entra: e outro titular. A direcao ja informa se a
        // mensagem foi escrita pelo proprio contato ou pela empresa.
        return mapear(entityManager.createNativeQuery("""
                SELECT m.direction, m.content_type, m.text_content, m.created_at
                  FROM message m
                  JOIN conversation c
                    ON c.tenant_id = m.tenant_id AND c.id = m.conversation_id
                 WHERE m.tenant_id = :tenant AND c.contact_id = :id
                 ORDER BY m.created_at
                """)
                .setParameter("tenant", TenantContext.obrigatorio())
                .setParameter("id", contatoId)
                .getResultList(),
                List.of("origem", "tipo", "conteudo", "em"));
    }

    private List<Map<String, String>> oportunidades(UUID contatoId) {
        return mapear(entityManager.createNativeQuery("""
                SELECT d.title, d.status, d.value_cents, d.created_at
                  FROM deal d
                 WHERE d.tenant_id = :tenant AND d.contact_id = :id
                 ORDER BY d.created_at
                """)
                .setParameter("tenant", TenantContext.obrigatorio())
                .setParameter("id", contatoId)
                .getResultList(),
                List.of("titulo", "situacao", "valorCentavos", "criadaEm"));
    }

    private List<Map<String, String>> tarefas(UUID contatoId) {
        return mapear(entityManager.createNativeQuery("""
                SELECT t.title, t.description, t.due_at, t.done_at
                  FROM task t
                 WHERE t.tenant_id = :tenant AND t.contact_id = :id
                 ORDER BY t.created_at
                """)
                .setParameter("tenant", TenantContext.obrigatorio())
                .setParameter("id", contatoId)
                .getResultList(),
                List.of("titulo", "descricao", "vencimentoEm", "concluidaEm"));
    }

    private List<Map<String, String>> mapear(List<?> linhas, List<String> nomes) {
        List<Map<String, String>> registros = new ArrayList<>(linhas.size());
        for (Object linha : linhas) {
            Object[] valores = (Object[]) linha;
            Map<String, String> campos = new LinkedHashMap<>();
            for (int i = 0; i < nomes.size(); i++) {
                campos.put(nomes.get(i), texto(valores[i]));
            }
            // A direcao OUTBOUND identifica a empresa, e nao a pessoa que
            // digitou: o atendente e outro titular.
            if ("OUTBOUND".equals(campos.get("origem"))) {
                campos.put("autor", MASCARA_INTERNA);
            }
            registros.add(campos);
        }
        return registros;
    }

    /**
     * Prazo declarado para a categoria, ou a ausencia dele — dita com todas as
     * letras. Escrever "indefinido" seria mais elegante e menos honesto.
     */
    private String prazo(String categoria) {
        return "prazo ainda não definido (" + categoria + ") — ver LGPD-002";
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }
}
