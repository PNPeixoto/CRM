# Sessão — backend:04 modelo organizacional e escopos

Data: 2026-08-01 22:43 (America/Montevideo)  
Branch: `main`  
Baseline: `793777d + working-tree`

## Resultado

Prompt 04 concluído e Gate A fechado. O modelo separa identidade interna de
cliente final, permite multiunidade sem duplicação e mantém papel separado de
escopo.

## Mudanças

- V10: unidade, membership temporal, papel, permissão e atribuição de escopo,
  todos com RLS e FKs compostas por tenant.
- Contato declara `PERSON` (B2C) ou `ORGANIZATION` (B2B) com default compatível.
- Módulo `organization` expõe somente `OrganizationAccess`; API HTTP lista
  contextos autorizados.
- Escopos persistidos: tenant, unidade e próprio registro. Rede/equipe aguardam
  identidades/agregados com FK real.
- Seed dev repeatable adiciona unidade e proprietário sem tocar produção.
- Modelo, cardinalidades, ciclos de vida, hierarquia, glossário e ADR-0006.

## Evidências

- Gate rápido: 30 testes, 0 falhas/erros/ignorados.
- Cenários do modelo: 5 testes verdes — expiração, troca, multiunidade,
  B2B/B2C e vínculo cruzado.
- Integração completa: 41 testes com PostgreSQL/Redis reais, 0
  falhas/erros/ignorados, 48,7 s.
- Flyway limpo e atualização de V8 até V10 validados.
- Fronteiras Spring Modulith verdes com o novo módulo e API nomeada.

## Gate A

Prompts 01–04 concluídos; imagem/ambientes, testes, banco e modelo
organizacional possuem implementação e evidência. Gate A: fechado.

## Próximo

Executar `backend:05`, verificar a recomendação NIST vigente e produzir a
matriz do Gate B para autenticação/sessão.
