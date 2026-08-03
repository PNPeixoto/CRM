# ADR-0001 — Pacote de prompts v3 canônico

- Status: accepted
- Data: 2026-08-01

## Contexto

O roteiro monolítico e a revisão da v2 deixavam requisitos distribuídos entre
versões, tarefas grandes e arquivos de contexto disputados por todas as branches.
Também havia regras absolutas conflitantes sobre retenção, webhook, migrations e
camada de domínio.

## Decisão

`contexto/prompts/manifest.yaml`, versão 3, é a única fonte canônica do roteiro.
Ela substitui v1, v2 e o prompt monolítico legado. O pacote possui prompts
autossuficientes de 00 a 28, gates com evidência e slices adicionais 25B/25C
para manter o agente privado revisável.

Decisões futuras usam ADR individual. Logs de sessão usam timestamp, branch e
slug. Estado consolidado e status do manifesto só são atualizados na integração
à `main` ou por rotina de consolidação.

## Alternativas descartadas

- Manter v1 como base e v2 como patch: preservaria duas fontes de verdade.
- Criar apenas um novo prompt monolítico: continuaria impraticável para PRs.
- Reescrever o histórico de decisões: apagaria a rastreabilidade existente.

## Consequências

Há repetição intencional das regras essenciais em cada prompt para permitir uso
isolado. O histórico Git preserva o roteiro anterior, mas ele não é requisito.
Alterações globais no pacote exigem novo ADR quando mudarem ordem, gate ou
protocolo. Revisar esta decisão se o manifesto deixar de representar o fluxo
real ou voltar a gerar edições concorrentes frequentes.
