# Sessão 2026-08-08 — backend:09 e frontend:F5

- Branch observada: `main`
- Ambiente: Windows, Node 24, npm 11, JDK 25
- Responsável: Codex

## Entregue

- auditoria e consolidação da casca existente, sem biblioteca visual paralela;
- alvos principais elevados para 44 px e reflow do login medido a 320 px;
- contexto organizacional real consumido; a lista fabricada foi removida;
- unidades permanecem ocultas enquanto o ADR-0008 impedir recorte ponta a
  ponta, evitando rótulo de unidade sobre dados do tenant inteiro;
- TanStack Query 5.101.4, exata e encapsulada por ADR-0010;
- chaves com tenant, unidade, identidade, recurso e parâmetros;
- cache em memória cancelado/limpo antes de nova sessão e logout entre abas;
- páginas remotas migradas, visão geral deduplicada e inbox integrada à
  invalidação de push/reconexão;
- conclusão de tarefa com atualização otimista, rollback e reconciliação;
- React Router atualizado para 7.18.2, Nano ID para 3.3.18 e a exceção antiga
  removida. O parser YAML só de build ganhou exceção específica após duas
  correções forçadas quebrarem `api:check` e serem revertidas.

## Evidências

- frontend: 109 testes em 23 arquivos, lint sem erros, build, `api:check` e
  auditoria sem vulnerabilidade alta/crítica fora da exceção de build;
- backend: gate rápido com 51 testes verdes;
- gate completo de backend tentou executar, mas parou antes dos testes porque
  Docker não estava disponível; nenhum código backend foi alterado;
- navegador real: login acessível a 320 × 568, sem overflow horizontal e com
  quatro controles principais de 44 px;
- testes F5: isolamento, troca, corrida, logout, rollback e invalidação seletiva.

## Decisões e limites

ADR-0010 registra adoção, custo e remoção do cache. O documento
`frontend/ESTADO-SERVIDOR.md` é a política executável. A auditoria de
acessibilidade registra NVDA/VoiceOver e zoom nativo 200% como provas manuais do
F10, sem tratá-las como executadas.

## Próximo passo

`backend:10` e `frontend:F6` estão liberados. O Gate C continua aberto até os
demais prompts obrigatórios da fase.
