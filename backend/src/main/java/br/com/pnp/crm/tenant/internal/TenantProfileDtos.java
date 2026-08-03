package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.tenant.api.BusinessSegment;
import br.com.pnp.crm.tenant.api.TenantPresentation;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Locale;

final class TenantProfileDtos {

    private TenantProfileDtos() {
    }

    record PerfilInicialRequest(@NotNull(message = "Escolha um segmento.") BusinessSegment segmento) {
    }

    record ApresentacaoResponse(
            BusinessSegment segmento,
            int versaoPreset,
            boolean onboardingConcluido,
            List<NavegacaoResponse> navegacao,
            FunilResponse funilPadrao) {

        static ApresentacaoResponse de(TenantPresentation presentation) {
            return new ApresentacaoResponse(
                    presentation.businessSegment(),
                    presentation.presetVersion(),
                    presentation.onboardingCompleted(),
                    presentation.navigation().stream()
                            .map(item -> new NavegacaoResponse(
                                    item.routeId(), item.label(),
                                    item.group().name().toLowerCase(Locale.ROOT),
                                    item.order(), item.visible()))
                            .toList(),
                    new FunilResponse(
                            presentation.defaultPipeline().name(),
                            presentation.defaultPipeline().stages().stream()
                                    .map(stage -> new EtapaResponse(
                                            stage.name(), stage.position(), stage.won(), stage.lost()))
                                    .toList()));
        }
    }

    record NavegacaoResponse(String routeId, String rotulo, String grupo, int ordem, boolean visivel) {
    }

    @Schema(name = "TenantFunilResponse")
    record FunilResponse(String nome, List<EtapaResponse> etapas) {
    }

    @Schema(name = "TenantEtapaResponse")
    record EtapaResponse(String nome, int posicao, boolean ganho, boolean perda) {
    }
}
