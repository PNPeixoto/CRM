package br.com.pnp.crm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeTestesTest {

    @Test
    void nenhumTesteEhIgnoradoOuUsaTagDeQuarentenaSemContrato() throws Exception {
        List<String> violacoes = new ArrayList<>();

        for (Class<?> classe : classesDeTeste()) {
            if (classe.isAnnotation()) {
                continue;
            }
            if (classe.isAnnotationPresent(Disabled.class)) {
                violacoes.add(classe.getName() + " usa @Disabled");
            }
            validarTagsDiretas(classe, classe.getName(), violacoes);
            ValidaQuarentenaExtension.validar(classe);

            for (Method metodo : classe.getDeclaredMethods()) {
                String local = classe.getName() + "#" + metodo.getName();
                if (metodo.isAnnotationPresent(Disabled.class)) {
                    violacoes.add(local + " usa @Disabled");
                }
                validarTagsDiretas(metodo, local, violacoes);
                ValidaQuarentenaExtension.validar(metodo);
            }
        }

        assertThat(violacoes).as("violações da política de testes").isEmpty();
    }

    private static List<Class<?>> classesDeTeste() throws IOException, ClassNotFoundException {
        Path raiz = Path.of("target", "test-classes", "br", "com", "pnp", "crm");
        List<Class<?>> classes = new ArrayList<>();
        try (var arquivos = Files.walk(raiz)) {
            for (Path arquivo : arquivos.filter(Files::isRegularFile).toList()) {
                String relativo = raiz.relativize(arquivo).toString();
                if (!relativo.endsWith(".class") || relativo.contains("$")) {
                    continue;
                }
                String nome = "br.com.pnp.crm." + relativo
                        .substring(0, relativo.length() - ".class".length())
                        .replace('/', '.')
                        .replace('\\', '.');
                classes.add(Class.forName(nome, false,
                        Thread.currentThread().getContextClassLoader()));
            }
        }
        return classes;
    }

    private static void validarTagsDiretas(java.lang.reflect.AnnotatedElement elemento,
                                           String local,
                                           List<String> violacoes) {
        for (Tag tag : elemento.getAnnotationsByType(Tag.class)) {
            if ("quarentena".equals(tag.value())) {
                violacoes.add(local + " deve usar @Quarentena, não @Tag diretamente");
            }
        }
    }
}
