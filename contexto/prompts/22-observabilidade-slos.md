---
id: "22"
title: "Observabilidade e SLOs"
phase: "operations_compliance"
risk: "medium"
prerequisites: ["08", "12", "15"]
produces: ["telemetria sanitizada", "SLOs mensuráveis", "alertas acionáveis"]
gate: "E"
---

# Prompt 22 — Observabilidade e SLOs

## Objetivo

Faça o sistema responder “o que quebrou, para quem e desde quando” sem registrar
conteúdo ou segredo e sem confundir auditoria com log operacional.

## Protocolo obrigatório

Não invente SLO comercial. Proponha baseline mensurável e pare se ele virar
compromisso contratual. Logs não recebem senha, token, cookie, segredo, payload,
mensagem, arquivo ou dado sensível. Identificadores de tenant/usuário seguem
política de acesso e retenção.

## Trabalho

1. Padronize logs estruturados, trace/request/correlation IDs e propagação entre
   HTTP, jobs, eventos e adapters.
2. Meça latência, erros, saturação, filas, retries, conexões e dependências.
3. Defina SLI/SLO e orçamento de erro para jornadas críticas.
4. Crie alertas com ação/runbook; evite alerta sem dono ou limiar medido.
5. Separe liveness/readiness e proteja endpoints administrativos/métricas.
6. Aplique sampling, cardinalidade e retenção que não inviabilizem custo/operação.

## Testes e aceite

- falha injetada é rastreável ponta a ponta por correlação;
- scan confirma ausência de material sensível em logs;
- alertas de fila, integração, agente, backup e erro apontam runbook;
- dashboards mostram baseline e orçamento, não apenas gráficos decorativos;
- retenção e acesso da telemetria estão documentados.
