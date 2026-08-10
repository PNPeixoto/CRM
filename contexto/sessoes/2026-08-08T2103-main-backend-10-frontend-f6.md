# Sessão 2026-08-08 — backend:10, frontend:F6 e F7:contatos

- Branch observada: `main`
- Ambiente: Windows, Node 24, npm 11, JDK 25, Docker disponível
- Responsável: Codex

## Recorte vertical

- Jornada P0: criar um contato.
- Ator: usuário autenticado com a permissão `contacts.write`.
- Resultado: contato persistido no tenant da credencial, atribuído somente a
  responsável permitido e visível de acordo com o alcance organizacional.
- Limite deliberado: criação, formulário e lista de contatos. Edição,
  importação, automações e outros domínios permanecem fora desta fatia.

## Entregue

- regra de criação extraída do controller para serviço e política de domínio;
- `Idempotency-Key` opcional, validado e persistido por tenant;
- replay com a mesma chave e conteúdo devolve o contato original;
- reutilização da chave com conteúdo diferente falha com código público
  `CHAVE_IDEMPOTENCIA_EM_CONFLITO`;
- lock transacional e índice único impedem duplicação concorrente;
- migration aditiva V14 e versão estrutural esperada atualizada;
- OpenAPI e tipos TypeScript regenerados com o novo cabeçalho;
- formulário de contato preserva valores, associa erros RFC 9457 aos campos,
  anuncia o resumo e foca o primeiro campo inválido;
- submissões simultâneas são bloqueadas e a escrita só admite repetição com
  chave explícita;
- dinheiro usa centavos inteiros, data civil não muda por fuso e
  `datetime-local` é convertido pelo fuso IANA `America/Sao_Paulo`;
- segredos opcionais em branco são omitidos e nunca repovoados;
- execução repetível `frontend:F7:contatos`: filtro e página persistidos na
  URL, paginação limitada, ordenação permitida e estado de acesso negado
  distinto do estado vazio.

Nenhum evento de domínio foi criado: esta fatia não introduziu desacoplamento
real que justificasse publicação assíncrona.

## Evidências

- backend completo: 128 testes, 0 falhas, 0 erros e build verde;
- migração validada tanto de banco vazio quanto de V8 até V14 em PostgreSQL
  descartável;
- frontend completo: 122 testes em 26 arquivos, todos verdes;
- build de produção e `api:check` verdes;
- lint sem erros; permanecem três avisos preexistentes de Fast Refresh em
  `AuthContext`, `TenantPresentationContext` e `button`;
- replay, conflito, chave inválida, contrato HTTP, preservação/foco de erros,
  precisão de valores/tempo, segredo em branco e URL da lista possuem testes.

## Operação e rollback

O backend local em execução precisa ser reiniciado para o Flyway aplicar V14
ao volume de desenvolvimento. A migration é aditiva; rollback de aplicação
mantém coluna e índice sem perda. Se a funcionalidade for retirada no futuro,
uma nova migration deve remover a estrutura somente depois de confirmar que
nenhum cliente depende do cabeçalho. V14 nunca deve ser editada após aplicada.

## Próximo passo

`backend:11` está liberado. `frontend:F7` permanece `ready` no manifesto porque
é repetível por domínio; esta execução específica de contatos está concluída.
O Gate C permanece aberto até o Prompt 11 e as demais evidências obrigatórias.
