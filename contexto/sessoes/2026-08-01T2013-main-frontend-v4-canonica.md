# Sessão — Trilha frontend v4 canônica

- Data: 2026-08-01
- Branch observada: `main` com working tree existente
- Escopo: incorporar a trilha frontend v3 e as correções da revisão técnica

## Entregue

- pacote complementar em `contexto/prompts/frontend/`, com preâmbulo, README,
  manifesto e 16 prompts F0–F13;
- F0A antecipa infraestrutura de testes e F4A cobre segurança do navegador e
  cadeia de dependências;
- OpenAPI isolado por adaptadores, RFC 9457, modelagem explícita de tempo e
  dinheiro, realtime adaptativo, WCAG 2.2 AA, Core Web Vitals e telemetria por
  allowlist;
- IDs qualificados e taxonomia de bloqueio para permitir trabalho paralelo sem
  confundir demonstração, piloto externo e produção;
- integração com os Prompts 09, 10 e 14 e com os Gates B–E;
- ADR-0005 registra a decisão e o estado consolidado aponta os próximos passos.

## Limites

Esta sessão alterou somente documentação e governança. Nenhum prompt frontend
foi marcado como concluído e nenhum código funcional foi modificado por este
trabalho.

## Validação

- 31 prompts backend, 16 frontend e 47 nós combinados encontrados;
- todos os arquivos declarados existem e os metadados coincidem;
- dependências qualificadas resolvem e o grafo combinado é acíclico;
- estado consolidado permanece abaixo de 150 linhas;
- verificação de whitespace não acusou erro.
