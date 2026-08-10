package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.tenant.api.BusinessSegment;
import br.com.pnp.crm.tenant.api.DefaultPipelineTemplate;
import br.com.pnp.crm.tenant.api.NavigationGroup;
import br.com.pnp.crm.tenant.api.NavigationItem;
import br.com.pnp.crm.tenant.api.PipelineStageTemplate;
import br.com.pnp.crm.tenant.api.TenantPresentation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Único lugar que conhece diferenças de apresentação entre segmentos. */
@Component
class SegmentPresetCatalog {

    static final int CURRENT_VERSION = 1;

    private static final Map<String, RouteDefault> ROUTES = defaults();

    private static final Map<BusinessSegment, Preset> PRESETS_V1 = Map.of(
            BusinessSegment.GENERAL_SERVICES, new Preset(
                    Map.of("contacts", "Contatos", "pipelines", "Oportunidades"),
                    List.of("dashboard", "inbox", "contacts", "pipelines", "tasks", "calendar",
                            "reports", "units", "teams", "integrations", "settings"),
                    pipeline("Funil de vendas", List.of(
                            stage("Novo lead"), stage("Contato feito"), stage("Proposta enviada"),
                            stage("Negociação"), won("Ganho"), lost("Perdido")))),
            BusinessSegment.CONFECTIONERY, new Preset(
                    Map.of("contacts", "Clientes", "pipelines", "Encomendas",
                            "calendar", "Agenda", "assets", "Produtos"),
                    List.of("dashboard", "pipelines", "calendar", "contacts", "assets", "inbox",
                            "tasks", "reports", "units", "teams", "integrations", "settings"),
                    pipeline("Encomendas", List.of(
                            stage("Novo orçamento"), stage("Orçamento enviado"),
                            stage("Aguardando sinal"), stage("Confirmado"), stage("Produção"),
                            stage("Pronto"), won("Entregue"), lost("Perdido")))),
            BusinessSegment.RESTAURANT, new Preset(
                    Map.of("contacts", "Clientes", "pipelines", "Pedidos",
                            "assets", "Cardápio", "bookings", "Reservas"),
                    List.of("dashboard", "pipelines", "bookings", "assets", "contacts", "inbox",
                            "tasks", "reports", "units", "teams", "integrations", "settings"),
                    pipeline("Pedidos", List.of(
                            stage("Recebido"), stage("Confirmado"), stage("Em preparo"),
                            stage("Pronto"), won("Finalizado"), lost("Cancelado")))),
            BusinessSegment.RENTAL, new Preset(
                    Map.of("contacts", "Clientes", "pipelines", "Orçamentos",
                            "bookings", "Reservas", "assets", "Produtos e ativos"),
                    List.of("dashboard", "bookings", "pipelines", "assets", "contacts", "inbox",
                            "tasks", "calendar", "reports", "units", "teams", "integrations", "settings"),
                    pipeline("Locações", List.of(
                            stage("Novo contato"), stage("Orçamento"),
                            stage("Aguardando confirmação"), stage("Reservado"),
                            stage("Entregue"), won("Concluído"), lost("Perdido")))));

    private static final Map<Integer, Map<BusinessSegment, Preset>> PRESETS_BY_VERSION =
            Map.of(1, PRESETS_V1);

    TenantPresentation resolve(BusinessSegment segment, int version, boolean onboardingCompleted) {
        Map<BusinessSegment, Preset> presets = PRESETS_BY_VERSION.get(version);
        if (presets == null) {
            throw new IllegalStateException("Versão de preset desconhecida: " + version);
        }
        Preset preset = presets.get(segment);
        if (preset == null) {
            throw new IllegalArgumentException("Segmento sem preset: " + segment);
        }
        return new TenantPresentation(segment, version, onboardingCompleted,
                navigation(preset), preset.pipeline());
    }

    TenantPresentation preview(BusinessSegment segment, boolean onboardingCompleted) {
        return resolve(segment, CURRENT_VERSION, onboardingCompleted);
    }

    private List<NavigationItem> navigation(Preset preset) {
        Set<String> visible = Set.copyOf(preset.visibleOrder());
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < preset.visibleOrder().size(); index++) {
            order.put(preset.visibleOrder().get(index), (index + 1) * 10);
        }

        return ROUTES.entrySet().stream()
                .map(entry -> new NavigationItem(
                        entry.getKey(),
                        preset.labels().getOrDefault(entry.getKey(), entry.getValue().label()),
                        entry.getValue().group(),
                        order.getOrDefault(entry.getKey(), 1000 + entry.getValue().defaultOrder()),
                        visible.contains(entry.getKey())))
                .toList();
    }

    private static DefaultPipelineTemplate pipeline(String name, List<StageKind> stages) {
        List<PipelineStageTemplate> templates = java.util.stream.IntStream.range(0, stages.size())
                .mapToObj(index -> new PipelineStageTemplate(
                        stages.get(index).name(), (index + 1) * 10,
                        stages.get(index).won(), stages.get(index).lost()))
                .toList();
        return new DefaultPipelineTemplate(name, templates);
    }

    private static StageKind stage(String name) {
        return new StageKind(name, false, false);
    }

    private static StageKind won(String name) {
        return new StageKind(name, true, false);
    }

    private static StageKind lost(String name) {
        return new StageKind(name, false, true);
    }

    private static Map<String, RouteDefault> defaults() {
        Map<String, RouteDefault> routes = new LinkedHashMap<>();
        add(routes, "dashboard", "Visão geral", NavigationGroup.OPERACAO);
        add(routes, "inbox", "Caixa de entrada", NavigationGroup.OPERACAO);
        add(routes, "contacts", "Contatos", NavigationGroup.OPERACAO);
        add(routes, "pipelines", "Oportunidades", NavigationGroup.OPERACAO);
        add(routes, "tasks", "Tarefas", NavigationGroup.OPERACAO);
        add(routes, "calendar", "Agenda", NavigationGroup.OPERACAO);
        add(routes, "bookings", "Reservas", NavigationGroup.OPERACAO);
        add(routes, "assets", "Produtos e ativos", NavigationGroup.OPERACAO);
        add(routes, "units", "Unidades", NavigationGroup.GESTAO);
        add(routes, "teams", "Equipes", NavigationGroup.GESTAO);
        add(routes, "automations", "Automações", NavigationGroup.PLATAFORMA);
        add(routes, "integrations", "Canais e números", NavigationGroup.PLATAFORMA);
        add(routes, "campaigns", "Campanhas", NavigationGroup.GESTAO);
        add(routes, "reports", "Relatórios", NavigationGroup.GESTAO);
        add(routes, "audit", "Auditoria", NavigationGroup.PLATAFORMA);
        add(routes, "settings", "Configurações", NavigationGroup.PLATAFORMA);
        return Map.copyOf(routes);
    }

    private static void add(Map<String, RouteDefault> routes, String id, String label,
                            NavigationGroup group) {
        routes.put(id, new RouteDefault(label, group, (routes.size() + 1) * 10));
    }

    private record RouteDefault(String label, NavigationGroup group, int defaultOrder) {
    }

    private record StageKind(String name, boolean won, boolean lost) {
    }

    private record Preset(Map<String, String> labels, List<String> visibleOrder,
                          DefaultPipelineTemplate pipeline) {
        private Preset {
            labels = Map.copyOf(labels);
            visibleOrder = List.copyOf(visibleOrder);
        }
    }
}
