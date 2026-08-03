package br.com.pnp.crm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Snapshot produzido pelo backend real; alteração de contrato exige regeneração explícita. */
@TesteDeIntegracao
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Path SNAPSHOT = Path.of("openapi", "openapi.json");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void publishedContractMatchesVersionedSnapshot() throws Exception {
        String published = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode actual = objectMapper.readTree(published);
        String normalized = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(actual) + System.lineSeparator();

        if (Boolean.getBoolean("openapi.write")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, normalized, StandardCharsets.UTF_8);
        }

        assertThat(SNAPSHOT).as("gere com -Dopenapi.write=true").exists();
        JsonNode expected = objectMapper.readTree(Files.readString(SNAPSHOT));
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.path("openapi").asText()).startsWith("3.1");
        assertThat(actual.path("components").path("schemas").has("ErroResponse")).isTrue();
    }
}
