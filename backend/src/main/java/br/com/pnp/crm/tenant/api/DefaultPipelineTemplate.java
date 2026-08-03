package br.com.pnp.crm.tenant.api;

import java.util.List;

/** Template versionado; alterações posteriores do tenant não dependem dele. */
public record DefaultPipelineTemplate(String name, List<PipelineStageTemplate> stages) {

    public DefaultPipelineTemplate {
        stages = List.copyOf(stages);
    }
}
