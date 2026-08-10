package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.tenant.api.BusinessSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegmentPresetCatalogTest {

    private final SegmentPresetCatalog catalog = new SegmentPresetCatalog();

    @Test
    void resolvesLabelsAndPipelinesForEverySegment() {
        Map<BusinessSegment, String> pipelineNames = Map.of(
                BusinessSegment.GENERAL_SERVICES, "Funil de vendas",
                BusinessSegment.CONFECTIONERY, "Encomendas",
                BusinessSegment.RESTAURANT, "Pedidos",
                BusinessSegment.RENTAL, "Locações");

        Map<BusinessSegment, List<String>> stages = Map.of(
                BusinessSegment.GENERAL_SERVICES,
                List.of("Novo lead", "Contato feito", "Proposta enviada", "Negociação", "Ganho", "Perdido"),
                BusinessSegment.CONFECTIONERY,
                List.of("Novo orçamento", "Orçamento enviado", "Aguardando sinal", "Confirmado",
                        "Produção", "Pronto", "Entregue", "Perdido"),
                BusinessSegment.RESTAURANT,
                List.of("Recebido", "Confirmado", "Em preparo", "Pronto", "Finalizado", "Cancelado"),
                BusinessSegment.RENTAL,
                List.of("Novo contato", "Orçamento", "Aguardando confirmação", "Reservado",
                        "Entregue", "Concluído", "Perdido"));

        for (BusinessSegment segment : BusinessSegment.values()) {
            var presentation = catalog.preview(segment, true);
            assertThat(presentation.defaultPipeline().name()).isEqualTo(pipelineNames.get(segment));
            assertThat(presentation.defaultPipeline().stages())
                    .extracting(stage -> stage.name())
                    .containsExactlyElementsOf(stages.get(segment));
            assertThat(presentation.navigation())
                    .anyMatch(item -> item.routeId().equals("contacts") && item.visible())
                    .anyMatch(item -> !item.visible());
        }
    }

    @Test
    void restaurantRemainsPresentationPresetOnly() {
        var presentation = catalog.preview(BusinessSegment.RESTAURANT, true);
        assertThat(presentation.navigation())
                .anyMatch(item -> item.routeId().equals("assets") && item.label().equals("Cardápio"))
                .anyMatch(item -> item.routeId().equals("bookings") && item.label().equals("Reservas"));
    }

    @Test
    void versaoPersistidaNuncaCaiSilenciosamenteNoPresetAtual() {
        assertThat(catalog.resolve(BusinessSegment.GENERAL_SERVICES, 1, true).presetVersion())
                .isEqualTo(1);
        assertThatThrownBy(() -> catalog.resolve(BusinessSegment.GENERAL_SERVICES, 2, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Versão de preset desconhecida");
    }
}
