---
id: "01"
title: "Docker e ambientes reproduzíveis"
phase: "foundation"
risk: "medium"
prerequisites: ["00"]
produces: ["ambiente dev", "imagem da aplicação", "configuração por ambiente"]
gate: "A"
---

# Prompt 01 — Docker e ambientes

## Objetivo

Torne desenvolvimento e build reproduzíveis sem embutir segredos nem aproximar
configurações locais inseguras da produção.

## Protocolo obrigatório

Leia o contexto canônico e confirme o estado do código. Preserve o stack e as
dependências existentes. Só pare pelas condições críticas do protocolo v3; em
escolha reversível mantenha o comportamento atual. Valores secretos nunca vão
para Git, logs ou evidências. Migration só existe se houver mudança persistente.

## Trabalho

1. Remova composições concorrentes e mantenha uma fonte explícita por ambiente.
2. Modele dev com PostgreSQL 17, Redis/Valkey e dependências realmente usadas;
   health checks e volumes devem ter finalidade clara.
3. Crie imagem multi-stage, runtime mínimo, usuário sem privilégio, readiness e
   tags imutáveis. Não deixe fonte, Maven, cache ou segredo na imagem final.
4. Separe configuração de dev/test/prod e valide falha fechada em produção.
5. Banco e cache não ficam publicamente expostos em produção. Proxy confia em
   forwarded headers apenas de origem conhecida.
6. Documente Windows/Linux sem comandos destrutivos e sem credenciais reais.

## Testes e aceite

- build limpo da imagem em ambiente descartável;
- processos rodam como usuário não-root;
- health/readiness distinguem vivo de pronto;
- scan da imagem e secret scan sem crítico não tratado;
- dev sobe do zero e não depende de estado manual oculto;
- rollback usa tag fixa, nunca somente `latest`.

Registre commit, ambiente, data, comandos, resultados, artefatos e responsável.
