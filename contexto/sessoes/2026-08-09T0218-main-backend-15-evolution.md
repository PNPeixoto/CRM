# Sessão — Backend 15 e laboratório Evolution

- Data: 2026-08-09 02:18 (America/Montevideo)
- Branch observada: `main`
- Escopo: Prompt backend 15 e continuidade do teste multicanal Evolution

## Entregue

- Migration V20 com definições/versionamento imutável, execuções, passos,
  compensações, transições, RLS forçado e reserva concorrente estreita.
- API de criação, publicação, ativação, pausa, execução, dry-run, consulta,
  retomada e cancelamento.
- Worker com lease, quotas, limite de concorrência, deadline, retry seguro,
  causalidade, deduplicação e sanitização.
- Gatilho `MESSAGE_RECEIVED` após commit, transportando somente identificadores.
- Permissões `automations.read` e `automations.write` para o papel máximo do
  ambiente de desenvolvimento.
- OpenAPI e tipos do frontend regenerados.
- Evolution API 2.3.7, dependências, instância `pnp-teste` e webhook interno
  permanecem configurados. Um QR novo foi emitido e o estado remoto ficou
  `connecting`; pareamento humano ainda pendente.

## Decisões

- Sem conector HTTP, agente privado ou billing neste prompt.
- Dry-run não chama efeito externo.
- Retry apenas quando a ação declara segurança.
- Efeito reversível registra compensação; irreversível ou em voo recebe marca
  explícita, sem compensação automática.
- Sem payload, texto de mensagem ou segredo na trilha/log do motor.

## Evidências

- Backend completo: 176 testes, 0 falhas.
- Testes direcionados do motor/migração/segurança: 15, 0 falhas.
- Frontend: 128 testes, build e contrato OpenAPI verdes.
- Lint verde com três avisos preexistentes de Fast Refresh.
- Migração V8→V20 e banco vazio→V20 validados em PostgreSQL 17 real.
- Imagem local reconstruída sem recriar volumes; readiness `UP` e histórico
  Flyway confirma `20:true`. Evolution e Telegram continuaram ativos.

## Próximo passo

1. Renovar o QR da Evolution, parear o aparelho e provar entrada/saída na Inbox.
2. Executar `backend:16` (conector HTTP seguro). F9 segue condicionado a
   evidência de volume.
