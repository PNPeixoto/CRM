package br.com.pnp.crm;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nenhum evento de aplicacao carrega conteudo de cliente.
 *
 * <p><b>Por que este teste existe.</b> O Spring Modulith serializa todo evento
 * publicado em {@code event_publication} — a unica tabela do schema sem RLS,
 * sem prazo de retencao e com acesso total do runtime. Um campo de texto num
 * evento vira, sem ninguem decidir, uma copia permanente do conteudo do
 * cliente fora do isolamento por tenant e fora de qualquer expurgo.
 *
 * <p>Foi exatamente o que aconteceu: {@code MensagemRecebidaEvent} declarava
 * {@code String texto}, ninguem o lia, e 871 copias em claro se acumularam.
 * Registrado como {@code LGPD-001}.
 *
 * <p>A regra e grosseira de proposito — proibe texto livre em evento, e nao
 * apenas o campo que falhou. Acrescentar {@code String} a um evento e o gesto
 * natural de quem precisa de contexto; a barreira precisa estar no gesto, nao
 * no nome do campo.
 */
class PrivacidadeDeEventosTest {

    /**
     * Texto que descreve o proprio sistema, e nao a pessoa.
     *
     * <p>Tipo e chave tecnica sao seguros porque vem de um catalogo fechado do
     * codigo. O que nao pode entrar e dado de titular: mensagem, nome,
     * e-mail, telefone, endereco.
     */
    private static final Set<String> TEXTO_TECNICO_PERMITIDO = Set.of(
            "eventType", "tipo", "acao", "status", "motivo", "chaveIdempotente");

    @Test
    void nenhumEventoDeAplicacaoDeclaraTextoLivre() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Class<?> evento : eventosDeAplicacao()) {
            for (RecordComponent campo : evento.getRecordComponents()) {
                if (campo.getType() != String.class) continue;
                if (TEXTO_TECNICO_PERMITIDO.contains(campo.getName())) continue;
                violacoes.add(evento.getSimpleName() + '.' + campo.getName());
            }
        }

        assertThat(violacoes)
                .as("evento serializado em event_publication, que nao tem RLS nem retencao")
                .isEmpty();
    }

    @Test
    void aRegraCobreOCampoQueFalhou() {
        // Guarda contra o teste virar decorativo: se `MensagemRecebidaEvent`
        // deixar de existir ou de ser um record, o teste acima passaria vazio.
        assertThat(br.com.pnp.crm.conversation.api.MensagemRecebidaEvent.class.isRecord()).isTrue();
        assertThat(br.com.pnp.crm.conversation.api.MensagemRecebidaEvent.class
                .getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("texto", "mensagem", "conteudo");
    }

    /** Records de evento nos pacotes `api`, que sao os publicados entre modulos. */
    private static List<Class<?>> eventosDeAplicacao() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var leitor = new CachingMetadataReaderFactory(resolver);
        List<Class<?>> encontrados = new ArrayList<>();

        for (var recurso : resolver.getResources(
                "classpath*:br/com/pnp/crm/**/api/*Event.class")) {
            String nome = leitor.getMetadataReader(recurso).getClassMetadata().getClassName();
            try {
                Class<?> classe = Class.forName(nome, false,
                        Thread.currentThread().getContextClassLoader());
                if (classe.isRecord()) encontrados.add(classe);
            } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                // Classe fora do classpath de teste nao e evento de aplicacao.
            }
        }
        assertThat(encontrados).as("eventos encontrados no classpath").isNotEmpty();
        return encontrados;
    }
}
