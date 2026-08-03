# Sessão 2026-08-01T1821 — main — Prompt 00 diagnóstico

## Objetivo

Reavaliar o estado real do CRM PNP com evidência verificável, sem corrigir o
produto, atualizar dependências ou alterar serviços externos.

## Resultado

- Diagnóstico completo registrado em
  `contexto/diagnosticos/2026-08-01-prompt-00.md`.
- Sistema classificado como alpha técnica funcional, ainda sem gate aprovado.
- P0 parcial; P1 fragmentário; P2 não iniciado.
- Manifesto tecnicamente acíclico, mas semanticamente incompatível com a regra
  de não iniciar P1 antes do fechamento do P0.
- Prompt 00 marcado como concluído na `main`.
- Estado consolidado não foi reescrito, conforme o aceite do próprio Prompt 00.

## Verificações

- Backend: JAR gerado com testes ignorados.
- Backend completo: 59 testes descobertos; 28 passaram e 31 foram bloqueados
  porque o Docker Desktop estava parado e o Testcontainers não encontrou o
  daemon.
- Frontend: 6 testes passaram, build passou, lint sem erros e com três avisos.
- Compose: configuração válida para PostgreSQL e Redis; sem app/proxy/MailHog.
- Memurai: `PONG`.
- Git: `793777d + working-tree`, branch `main`; alterações locais preservadas.
- `git diff --check`: sucesso.

## Próximo passo

Executar o Prompt 01. Antes do Prompt 13, consolidar a correção da ordem v3 para
que provedores/automações P1 não antecedam auditoria e os demais slices P0.

Nenhum segredo, cookie, payload, conteúdo de mensagem ou dado pessoal foi
registrado.
