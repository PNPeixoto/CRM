# Sessão — funil compartilhado do tenant PNP

**Data:** 2026-08-03  
**Escopo:** fazer `pnp/peixoto` e `pnp/atendente` operarem o mesmo quadro de
oportunidades sem ampliar contatos e tarefas.

## Diagnóstico

- Existe um único `Funil de vendas` no tenant `pnp`.
- As 9 oportunidades existentes estavam sem responsável.
- O papel `ATTENDANT` concedia `deals.read/write` em `OWN`; por isso a consulta
  aplicava o próprio usuário como filtro e escondia todos os cartões sem dono.
- `OWNER` usa `TENANT` e, corretamente para seu alcance, via o quadro completo.

## Decisão e correção

- O funil é uma superfície operacional compartilhada dentro da empresa.
- `deals.read` e `deals.write` foram movidas do papel `ATTENDANT` (`OWN`) para
  `ATTENDANT_SHARED` (`TENANT`).
- `contacts.*` e `tasks.*` permanecem em `OWN`.
- A repeatable migration de desenvolvimento remove concessões antigas antes de
  inserir as novas, mantendo volumes existentes e bancos novos consistentes.
- O banco local foi atualizado na mesma transação; nenhuma oportunidade, etapa,
  senha ou dado comercial foi recriado.

## Evidências

- Migração isolada: 2/2 testes aprovados.
- Backend completo: 112/112 testes aprovados.
- Banco local: `deals.read/write = TENANT`, `contacts.read` e `tasks.read = OWN`.
- As 9 oportunidades do PNP permanecem no único funil e agora são visíveis aos
  dois logins.
