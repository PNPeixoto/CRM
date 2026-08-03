---
id: "07"
title: "Baseline ASVS e threat models"
phase: "core_security"
risk: "high"
prerequisites: ["05", "06"]
produces: ["matriz ASVS", "threat models críticos", "backlog rastreável"]
gate: "B"
---

# Prompt 07 — Baseline ASVS e threat models

## Objetivo

Transforme segurança em requisitos verificáveis, usando OWASP ASVS vigente
nível 2 como alvo e nível 3 somente em controles de alto impacto justificados.

## Protocolo obrigatório

Consulte somente fontes oficiais e registre a versão/data; não confie em
documentação memorizada. Este prompt analisa e documenta, não altera contrato,
privacidade ou política de risco sem decisão. Nenhuma evidência contém segredo,
payload ou dado pessoal.

## Trabalho

1. Crie matriz `controle | aplicável | implementação | teste | evidência`.
2. Modele ameaças por fluxo: login/recuperação, tenant e unidade, webhook,
   mídia, WebSocket, automação/conector, exportação, billing e agente privado.
3. Para cada ameaça registre ativo, fronteira de confiança, abuso, impacto,
   controle preventivo/detectivo, teste e risco residual.
4. Inclua supply chain, segredo, dependência, imagem e pipeline.
5. Gere backlog priorizado; achado aceito exige responsável, prazo e fundamento.
6. Não crie um documento genérico desconectado dos endpoints e componentes.

## Aceite

- versão do ASVS e fontes oficiais estão identificadas;
- todos os fluxos críticos têm dono e teste planejado/existente;
- cada controle adotado aponta evidência reproduzível;
- secret/dependency scan integra o gate;
- risco crítico bloqueia Gate B; exceção informal não é aceite.
