---
id: "00"
title: "Diagnóstico verificável do repositório"
phase: "foundation"
risk: "low"
prerequisites: []
produces: ["inventário real", "mapa de lacunas", "ordem de execução confirmada"]
gate: null
---

# Prompt 00 — Diagnóstico verificável

## Objetivo

Avalie o estado real do CRM PNP antes de propor implementação. Este prompt é
somente diagnóstico: não corrija código, não atualize dependências e não altere
serviços externos.

## Protocolo obrigatório

Leia `CLAUDE.md` e os contextos 00, 01 e 02. Código, migrations e configuração
executável vencem documento desatualizado. Preserve alterações locais. Pare
somente diante de escolha irreversível, segurança/privacidade/contrato/cobrança,
ADR conflitante ou informação indispensável ausente; decisões reversíveis usam
o default menos invasivo e são declaradas como suposição.

## Trabalho

1. Inventarie stack, módulos, fronteiras, migrations, ambientes e scripts.
2. Compare implementação com P0/P1/P2 e não confunda arquivo existente com
   funcionalidade concluída.
3. Execute apenas verificações não destrutivas disponíveis: status do Git,
   compilação, testes, lint e inspeção de serviços; registre o que não rodou.
4. Classifique lacunas por impacto: segurança/dados, fluxo P0, operação e UX.
5. Mapeie dependências entre os prompts da v3 e aponte qualquer ordem inviável.
6. Não registre segredo, cookie, payload, conteúdo de mensagem ou dado pessoal.

## Saída e aceite

- relatório com evidência, não impressão subjetiva;
- tabela `implementado | parcial | ausente | bloqueado`;
- riscos P0/P1/P2 com motivo e próximo teste verificável;
- nenhuma mutação de produto;
- sessão registrada com timestamp e slug, sem reescrever o estado consolidado.

Para toda evidência informe commit, ambiente, data, comando, resultado,
artefato e responsável. Se não houver commit, use `working-tree`.
