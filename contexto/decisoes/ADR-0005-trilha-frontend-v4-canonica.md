# ADR-0005 — Trilha de frontend v4 canônica e complementar

- Status: accepted
- Data: 2026-08-01

## Contexto

A trilha de frontend v3 recebida fora do repositório cobria o ciclo F0–F13, mas
concentrava testes e segurança tardiamente, tinha sobreposição com os Prompts
09, 10 e 14 da trilha principal e não qualificava dependências cruzadas. A
revisão técnica também identificou lacunas em CSP/CSRF/XSS, supply chain,
RFC 9457, contrato OpenAPI, datas, dinheiro, telemetria e métricas SPA.

## Decisão

Adotar `contexto/prompts/frontend/manifest.yaml` versão 4 como trilha canônica
complementar, sem substituir a trilha principal/backend v3. IDs cruzados usam
`backend:<id>` e `frontend:<id>`.

A v4 inclui F0A para testes desde a fundação e F4A para segurança do navegador
e supply chain. F7 é repetível por domínio. Bloqueios são classificados como
`before-demo`, `before-external-pilot`, `before-production`,
`feature-activation:<recurso>` ou `evidence-triggered`.

O backend permanece dono de domínio, persistência, autorização, contrato e
sequência de eventos. A trilha frontend é dona de apresentação, adaptadores do
cliente, sessão no navegador, cache, acessibilidade e comportamento realtime.
Prompts 09/F1-F2, 10/F7 e 14/F8 são companheiros, não implementações rivais.

## Alternativas descartadas

- Inserir F0–F13 no manifesto principal: mistura cadências, causa colisão de IDs
  e transforma dependências paralelizáveis em uma fila artificial.
- Fundir tudo nos Prompts 09, 10 e 14: esconderia requisitos de segurança e
  qualidade do navegador e produziria PRs grandes.
- Manter a v3 sem ajustes: preservaria lacunas críticas e padrões ambíguos.

## Consequências e revisão

Passam a existir 31 definições na trilha principal e 16 na trilha frontend, não
47 passos obrigatoriamente seriais. Uma demonstração de tela percorre F0–F7 e
suas dependências; outros recursos obedecem seu próprio bloqueio ou gatilho.
Mudança de fronteira entre trilhas ou de taxonomia requer novo ADR.
