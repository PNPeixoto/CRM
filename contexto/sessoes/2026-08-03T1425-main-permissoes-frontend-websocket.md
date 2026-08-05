# Sessão — permissões do frontend e WebSocket local

**Data:** 2026-08-03  
**Escopo:** corrigir os 403 provocados pela interface de um atendente e restaurar
o WebSocket no servidor de desenvolvimento.

## Diagnóstico

- O token observado era válido e pertencia ao papel restrito `ATTENDANT`.
- Os 403 de `/api/relatorios/visao-geral` e `/api/canais` eram decisões corretas
  do backend: esse papel não possui `reports.read` nem `channels.read`.
- O frontend não consumia `GET /api/organizacao/permissoes`, mantinha esses itens
  no menu e descobria a negativa por tentativa.
- O Vite encaminhava `/api`, mas não o upgrade de `/ws` para o backend.

## Correções

- O provider de apresentação carrega também o mapa permissão → escopo.
- As rotas prontas declaram a permissão de leitura realmente exigida por sua API.
- Menu e guarda de rota usam os códigos atômicos; acesso direto sem permissão
  mostra 403 local sem disparar a chamada protegida.
- A tela de canais trata falhas assíncronas sem rejeição não capturada nem falso
  estado vazio.
- O proxy de desenvolvimento encaminha `/ws` com upgrade WebSocket.

## Evidências

- Frontend: 98/98 testes em 21 arquivos.
- Build TypeScript/Vite: aprovado.
- Lint: sem erros; permanecem 3 avisos conhecidos de Fast Refresh.
- Contrato OpenAPI: sincronizado.
- Handshake real em `ws://localhost:5174/ws`: aberto com sucesso pelo proxy.

## Resíduo

`/api/organizacao/contextos` ainda não alimenta o seletor de contexto; essa parte
de `AUTZ-002` permanece no Gate C.
