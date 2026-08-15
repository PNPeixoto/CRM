# Sessão 2026-08-15 — integração da versão externa e Instagram oficial

## Escopo consolidado

- Nove commits da versão em `C:\Users\Administrator\Desktop\CRM` foram
  integrados por fast-forward na branch `codex/integrar-versao-outra-maquina`.
- O trabalho não commitado de Instagram foi portado e concluído no projeto
  atual, sem incluir o relatório de revisão que já estava solto no workspace.
- A V26 adiciona o token separado de verificação do webhook e a reserva
  multitenant para reconciliar a assinatura `messages`.

## Instagram oficial

- Graph API v24.0 em `graph.instagram.com`, autenticação Bearer e respostas
  externas limitadas a 1 MiB.
- Callback GET/POST por conexão, HMAC-SHA256 sobre os bytes originais, validação
  da conta destinatária, replay idempotente e payload cifrado antes do `200`.
- Tradução de mensagens recebidas, envio de texto e reconciliação automática da
  assinatura; credenciais Meta são write-only e cifradas.
- A janela de 24 horas usa o instante de persistência da última entrada. Fora
  dela, o servidor encerra a tentativa sem chamar a Meta e a Inbox desabilita o
  composer até uma nova mensagem do contato.
- O formulário exige ID profissional, access token, app secret e verify token,
  e mostra o callback absoluto que deve ser cadastrado no painel Meta.

## Correções durante a integração

- O tipo de uma conexão existente não pode mais ser trocado por um `PUT`.
- Instagram incompleto não nasce ativo.
- A rota de Acessos exige `organization.manage`, o alvo de arraste do Kanban
  ganhou 44 px e a API demonstrativa escuta somente em `127.0.0.1`.
- TypeScript foi alinhado à versão 5.9 suportada pelo gerador OpenAPI; instalação
  limpa com `npm ci` e auditoria sem vulnerabilidades.

## Evidências

- Backend: 283 testes, V1→V26 e V8→V26, sem falhas.
- Frontend: 155 testes, build de produção, lint e `api:check` concluídos.
- Lint mantém três avisos preexistentes de Fast Refresh, sem erros.

## Pendente fora do código

O ensaio real exige app Meta e negócio aprovados, conta profissional, permissão
`instagram_business_manage_messages`, credenciais válidas e callback HTTPS
público. Produção continua com `META_ENABLED=false` até essa configuração ser
liberada; o profile `dev` habilita a integração por padrão.
