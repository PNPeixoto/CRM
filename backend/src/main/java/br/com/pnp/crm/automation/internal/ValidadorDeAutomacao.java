package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationAction;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Valida limites e prova que o grafo e finito antes de criar uma versao. */
@Component
class ValidadorDeAutomacao {

    private final AutomationActionRegistry actions;
    private final int maxPassos;
    private final int maxRamificacoes;
    private final int maxDuracaoSegundos;

    ValidadorDeAutomacao(
            AutomationActionRegistry actions,
            @Value("${app.automation.max-steps:50}") int maxPassos,
            @Value("${app.automation.max-branches:20}") int maxRamificacoes,
            @Value("${app.automation.max-duration-seconds:300}") int maxDuracaoSegundos) {
        this.actions = actions;
        this.maxPassos = Math.clamp(maxPassos, 1, 500);
        this.maxRamificacoes = Math.clamp(maxRamificacoes, 0, 500);
        this.maxDuracaoSegundos = Math.clamp(maxDuracaoSegundos, 1, 86400);
    }

    Resultado validar(AutomationModel.EspecificacaoRequest request) {
        if (request == null || request.passos() == null || request.passos().isEmpty()) {
            throw invalida("Informe ao menos um passo.");
        }
        if (request.passos().size() > maxPassos) {
            throw invalida("A automacao excede o limite de passos.");
        }
        if (request.duracaoMaximaSegundos() < 1
                || request.duracaoMaximaSegundos() > maxDuracaoSegundos) {
            throw invalida("A automacao excede o limite de duracao.");
        }

        Map<String, AutomationModel.PassoRequest> porChave = new HashMap<>();
        for (AutomationModel.PassoRequest passo : request.passos()) {
            if (passo == null || passo.chave() == null || passo.acao() == null) {
                throw invalida("Passo de automacao incompleto.");
            }
            if (porChave.putIfAbsent(passo.chave(), passo) != null) {
                throw invalida("A automacao possui chaves de passo repetidas.");
            }
            if (passo.maxTentativas() < 1 || passo.maxTentativas() > 5) {
                throw invalida("Quantidade de tentativas do passo invalida.");
            }
        }
        if (!porChave.containsKey(request.passoInicial())) {
            throw invalida("O passo inicial nao existe.");
        }

        int ramificacoes = 0;
        Map<String, Integer> predecessores = new HashMap<>();
        List<AutomationModel.PassoSnapshot> snapshots = new ArrayList<>();
        for (AutomationModel.PassoRequest passo : request.passos()) {
            List<String> proximos = passo.proximos() == null
                    ? List.of() : List.copyOf(passo.proximos());
            if (new HashSet<>(proximos).size() != proximos.size()) {
                throw invalida("Um passo nao pode repetir o mesmo destino.");
            }
            for (String proximo : proximos) {
                if (!porChave.containsKey(proximo)) {
                    throw invalida("A automacao referencia um passo inexistente.");
                }
                predecessores.merge(proximo, 1, Integer::sum);
            }
            ramificacoes += Math.max(0, proximos.size() - 1);
            AutomationAction action = actions.exigir(passo.acao());
            try {
                action.validarConfiguracao(passo.configuracao());
            } catch (IllegalArgumentException e) {
                throw invalida("Configuracao do passo invalida.");
            }
            if (!action.repeticaoSegura() && passo.maxTentativas() > 1) {
                throw invalida("Acao nao repetivel deve ter uma unica tentativa.");
            }
            snapshots.add(new AutomationModel.PassoSnapshot(
                    passo.chave(), passo.acao(), action.natureza(),
                    action.repeticaoSegura(), passo.maxTentativas(), proximos,
                    passo.configuracao()));
        }
        if (ramificacoes > maxRamificacoes) {
            throw invalida("A automacao excede o limite de ramificacoes.");
        }
        garantirAciclicoEAtingivel(request.passoInicial(), porChave);
        if (predecessores.getOrDefault(request.passoInicial(), 0) != 0
                || predecessores.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(request.passoInicial())
                        && entry.getValue() != 1)) {
            throw invalida("Cada passo deve ter um unico caminho de entrada.");
        }
        return new Resultado(new AutomationModel.EspecificacaoSnapshot(
                request.gatilho(), request.passoInicial(),
                request.duracaoMaximaSegundos(), List.copyOf(snapshots)), ramificacoes);
    }

    private void garantirAciclicoEAtingivel(
            String inicial, Map<String, AutomationModel.PassoRequest> passos) {
        Set<String> visitados = new HashSet<>();
        Set<String> emVisita = new HashSet<>();
        ArrayDeque<Quadro> pilha = new ArrayDeque<>();
        pilha.push(new Quadro(inicial, false));
        while (!pilha.isEmpty()) {
            Quadro quadro = pilha.pop();
            if (quadro.saindo()) {
                emVisita.remove(quadro.chave());
                visitados.add(quadro.chave());
                continue;
            }
            if (visitados.contains(quadro.chave())) {
                continue;
            }
            if (!emVisita.add(quadro.chave())) {
                throw invalida("Loops de automacao nao sao permitidos.");
            }
            pilha.push(new Quadro(quadro.chave(), true));
            List<String> proximos = passos.get(quadro.chave()).proximos();
            if (proximos != null) {
                for (int indice = proximos.size() - 1; indice >= 0; indice--) {
                    String proximo = proximos.get(indice);
                    if (emVisita.contains(proximo)) {
                        throw invalida("Loops de automacao nao sao permitidos.");
                    }
                    if (!visitados.contains(proximo)) {
                        pilha.push(new Quadro(proximo, false));
                    }
                }
            }
        }
        if (visitados.size() != passos.size()) {
            throw invalida("Todos os passos devem ser alcancaveis a partir do inicio.");
        }
    }

    private static RequisicaoInvalidaException invalida(String mensagem) {
        return new RequisicaoInvalidaException(mensagem);
    }

    record Resultado(AutomationModel.EspecificacaoSnapshot snapshot, int ramificacoes) {
    }

    private record Quadro(String chave, boolean saindo) {
    }
}
