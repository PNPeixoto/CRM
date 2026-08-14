package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.Permissao;

import java.util.List;

/** Papéis iniciais editáveis para uma operação comercial e de atendimento. */
final class PresetComercial {

    private PresetComercial() {
    }

    static List<Definicao> papeis() {
        return List.of(
                new Definicao("SDR", "SDR / Pré-vendas",
                        "Qualifica leads, registra oportunidades e organiza follow-ups.",
                        codigos(Permissao.CONTACTS_READ, Permissao.CONTACTS_WRITE,
                                Permissao.DEALS_READ, Permissao.DEALS_WRITE,
                                Permissao.TASKS_READ, Permissao.TASKS_WRITE,
                                Permissao.CONVERSATIONS_READ, Permissao.CONVERSATIONS_WRITE)),
                new Definicao("CLOSER", "Closer",
                        "Conduz negociações, propostas e fechamento de oportunidades.",
                        codigos(Permissao.CONTACTS_READ, Permissao.DEALS_READ,
                                Permissao.DEALS_WRITE, Permissao.TASKS_READ,
                                Permissao.TASKS_WRITE, Permissao.CONVERSATIONS_READ,
                                Permissao.CONVERSATIONS_WRITE)),
                new Definicao("ATENDENTE", "Atendente",
                        "Atende contatos, responde conversas e registra os próximos passos.",
                        codigos(Permissao.CONTACTS_READ, Permissao.CONTACTS_WRITE,
                                Permissao.DEALS_READ, Permissao.TASKS_READ,
                                Permissao.TASKS_WRITE, Permissao.CONVERSATIONS_READ,
                                Permissao.CONVERSATIONS_WRITE)),
                new Definicao("GESTOR_ATENDIMENTO", "Gestor de atendimento",
                        "Acompanha a operação, os canais e a fila da equipe de atendimento.",
                        codigos(Permissao.CONTACTS_READ, Permissao.TASKS_READ,
                                Permissao.TASKS_WRITE, Permissao.CONVERSATIONS_READ,
                                Permissao.CONVERSATIONS_WRITE, Permissao.CHANNELS_READ,
                                Permissao.REPORTS_READ)),
                new Definicao("GERENTE_COMERCIAL", "Gerente comercial",
                        "Gerencia a carteira, o funil, a equipe e os indicadores comerciais.",
                        codigos(Permissao.DASHBOARD_READ, Permissao.CONTACTS_READ,
                                Permissao.CONTACTS_WRITE, Permissao.DEALS_READ,
                                Permissao.DEALS_WRITE, Permissao.TASKS_READ,
                                Permissao.TASKS_WRITE, Permissao.CONVERSATIONS_READ,
                                Permissao.REPORTS_READ)));
    }

    private static List<String> codigos(Permissao... permissoes) {
        return java.util.Arrays.stream(permissoes).map(Permissao::codigo).toList();
    }

    record Definicao(String codigo, String nome, String descricao, List<String> permissoes) {
        Definicao {
            permissoes = List.copyOf(permissoes);
        }
    }
}
