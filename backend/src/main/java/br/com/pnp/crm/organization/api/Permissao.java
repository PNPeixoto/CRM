package br.com.pnp.crm.organization.api;

/**
 * Permissões atômicas, uma por ação.
 *
 * <p>Os códigos são exatamente os já semeados em {@code role_permission} — não
 * um vocabulário paralelo. Um enum, e não string solta no ponto de uso, porque
 * permissão errada digitada em texto só falha em produção: aqui o compilador
 * recusa.
 *
 * <p><b>Leitura e escrita são separadas de propósito.</b> Um perfil de consulta
 * — auditor, financeiro — precisa enxergar sem poder alterar, e um único
 * {@code contacts.manage} tornaria isso impossível sem inventar um segundo
 * código depois.
 */
public enum Permissao {

    DASHBOARD_READ("dashboard.read"),

    CONTACTS_READ("contacts.read"),
    CONTACTS_WRITE("contacts.write"),

    DEALS_READ("deals.read"),
    DEALS_WRITE("deals.write"),

    TASKS_READ("tasks.read"),
    TASKS_WRITE("tasks.write"),

    CONVERSATIONS_READ("conversations.read"),
    CONVERSATIONS_WRITE("conversations.write"),

    CHANNELS_READ("channels.read"),
    /**
     * Cobre o cadastro de credencial de canal. Deliberadamente a mesma
     * permissão de editar o canal: quem pode trocar o token do bot já controla
     * o canal inteiro, e separar daria falsa sensação de privilégio menor.
     */
    CHANNELS_WRITE("channels.write"),

    AUTOMATIONS_READ("automations.read"),
    AUTOMATIONS_WRITE("automations.write"),

    INTEGRATIONS_READ("integrations.read"),
    INTEGRATIONS_WRITE("integrations.write"),

    REPORTS_READ("reports.read"),

    /** Leitura da trilha corporativa; sempre exige alcance de todo o tenant. */
    AUDIT_READ("audit.read"),

    /**
     * Atender pedido de titular: exportar, corrigir e anonimizar.
     *
     * <p>Separada de {@link #ORGANIZATION_MANAGE} de proposito. Quem administra
     * a empresa nao precisa, por isso, ler o dado pessoal de todos os contatos
     * de uma vez — e quem responde a um pedido de titular precisa, sem herdar
     * o poder de mexer em papeis e canais. Juntar as duas transformaria a
     * resposta a um direito em privilegio de administrador.
     */
    PRIVACY_MANAGE("privacy.manage"),

    ORGANIZATION_MANAGE("organization.manage");

    private final String codigo;

    Permissao(String codigo) {
        this.codigo = codigo;
    }

    /** Código persistido em {@code role_permission.permission_code}. */
    public String codigo() {
        return codigo;
    }
}
