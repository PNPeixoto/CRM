---
id: "21"
title: "Relatórios e exportações seguras"
phase: "operations_compliance"
risk: "high"
prerequisites: ["06", "17", "18", "19"]
produces: ["métricas canônicas", "jobs de exportação", "downloads temporários"]
gate: "E"
---

# Prompt 21 — Relatórios e exportações

## Objetivo

Entregue relatórios reproduzíveis e exportações assíncronas que revalidam
autorização no momento da execução e do download.

## Protocolo obrigatório

Definição de métrica, timezone, moeda e versão de cálculo são parte do contrato.
Não exporte campo indisponível ao perfil. Arquivo não fica no web root nem em
link permanente. Evidência não contém o arquivo ou amostra de cliente.

## Trabalho

1. Mantenha catálogo canônico de métricas com fórmula, granularidade, timezone,
   moeda, versão e fonte.
2. Execute exportações como jobs limitados, canceláveis e idempotentes.
3. Revalide tenant/unidade/permissão na execução e no download.
4. Gere arquivo cifrado em storage privado, retenção curta e URL assinada curta.
5. Previna CSV/Formula Injection e imponha limites de linhas/colunas/tamanho.
6. Aplique mascaramento/minimização e registre correlação/auditoria.

## Testes e aceite

- resultado reconcilia com dados de origem e é reproduzível pela versão;
- mudança de permissão após solicitação impede execução/download;
- fórmula maliciosa é neutralizada;
- jobs concorrentes/repetidos não duplicam arquivo ou consumo;
- expiração remove arquivo e invalida link sem quebrar legal hold aplicável.
