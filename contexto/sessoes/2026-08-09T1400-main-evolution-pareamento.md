# Sessão 2026-08-09 — Evolution: pareamento real e pareamento pelo CRM

- Branch: `main`
- Commit base: `37f76d3` mais árvore de trabalho acumulada
- Ambiente: Windows 11, JDK 25.0.4, Node 24.18.0, Docker Desktop
- Restrição inicial: **não alterar banco, redis, portas ou roles**
- Restrição revista no fim da sessão: aplicar a V22 **sem quebrar o banco**

## Como a restrição foi tratada

A sessão teve duas fases, e o log registra as duas porque a segunda revogou
parte da primeira.

**Primeira fase, sob a restrição original.** Nenhuma migration aplicada, nenhum
SQL de escrita, nenhum serviço reiniciado, nenhuma porta ou role tocada. O
banco permaneceu na V21. As únicas mudanças de estado foram a sessão de
WhatsApp pareada e as linhas que o próprio fluxo de mensagens gravou — que era
o objeto do teste. O snapshot OpenAPI foi regenerado com Testcontainers, banco
efêmero; o PostgreSQL local não participou.

**Segunda fase, com autorização explícita.** A V22 foi aplicada. O
procedimento está em "Implantação da V22", ao fim deste documento.

## O que a análise encontrou

O adaptador, o webhook, o tradutor de `MESSAGES_UPSERT` e a reconciliação já
existiam e funcionavam. Faltava só o pareamento — e faltava de um jeito que o
documento escondia.

1. **O CRM nunca criava instância nem pedia QR.** Uma busca por
   `/instance/create` e `/instance/connect` no código não retornava nada.
   Parear exigia chamar a Evolution na mão, com a API key do servidor.
2. **O runbook afirmava que a reconciliação cria a instância.** Ela nunca
   criou: só repara webhook e observa estado. Quem seguisse o documento
   esperaria uma instância que não aparece.
3. **Havia um canal órfão provando isso.** O registro `teste`, no tenant `pnp`,
   apontava para a instância `peixoto`, inexistente na Evolution (404
   confirmado). A reconciliação tentava a cada dez segundos e remarcava `ERROR`
   indefinidamente. Foi só reportado, por decisão do responsável.
4. **O manager web da Evolution não funciona.** `CORS_ORIGIN` está restrita e
   navegação de topo não envia `Origin`, então a API responde 500. Descoberto
   ao tentar usá-lo para parear.

## Pareamento real, feito nesta sessão

| Etapa | Evidência |
|---|---|
| Pareamento | `connecting` → `open` em ~16 s |
| Promoção do canal | `DEGRADED` → `HEALTHY` sozinho, em menos de 12 s |
| Entrada | 3 mensagens `RECEIVED`, processadas em ~1 s, **1 tentativa cada**, sem falha e sem dead letter |
| Conversa | criada na Inbox, `OPEN`, atribuída ao canal Evolution |
| Saída | `SENT`, com id de provedor de 22 caracteres, **1 tentativa**, sem retry |

O "1 tentativa" nas duas direções é o que importa: o caminho funcionou de
primeira, sem o pipeline precisar reprocessar. O id do provedor na saída prova
que a mensagem deixou a Evolution, e não apenas que o CRM a marcou como
enviada.

Nenhum conteúdo de mensagem foi lido: a verificação usou direção, status,
tamanho e contagem.

## O que foi implementado

- `EvolutionAdapter.garantirInstancia` cria a instância só quando ela não
  existe. Recriar apagaria a sessão de quem já estava atendendo.
- `EvolutionAdapter.iniciarPareamento` devolve QR e código de pareamento.
- `POST /api/canais/{id}/pareamento`, exigindo `CHANNELS_WRITE` no tenant,
  auditado como evento de **credencial** — parear estabelece uma sessão capaz
  de enviar como aquele número — e respondendo `no-store`.
- Botão **Conectar WhatsApp** no cartão do canal, exibindo QR e código. Nada é
  persistido no navegador: o material vive no estado do componente e some ao
  fechar.

### Duas decisões que valem registro

**A criação vive no pareamento, não no worker.** Criar sessão de WhatsApp é ato
deliberado de uma pessoa. Um worker que criasse sozinho recriaria a instância
toda vez que alguém a apagasse, sem ninguém pedir — e foi justamente a
expectativa de criação automática que produziu o canal órfão.

**O endpoint recusa reparear sessão saudável.** Com a instância em `open`, ele
devolve o estado e não emite material novo. Pedir reinicia o pareamento no
provedor: um clique acidental derrubaria um WhatsApp em atendimento.

O QR e o código não entram em log, auditoria, métrica nem mensagem de erro. Os
records `Pareamento` e `PareamentoResponse` sobrescrevem `toString` porque o
padrão do Java imprimiria o conteúdo em qualquer log que interpolasse o objeto
— e há teste que verifica isso.

## Correção de terceiro achada no caminho

`AuditoriaCorporativaTest` não compilava: `audit.registrar` devolve `void` e
`TenantContext.executarComo` exige `Supplier<T>`. Isso impedia **toda** a suíte
backend de compilar, e é o bloqueio que o estado atual registrava para o
Prompt 17. Corrigido com corpo de lambda e retorno explícito.

## Evidências

| verificação | resultado |
|---|---|
| `EvolutionAdapterContractTest` | 7 testes, 0 falhas |
| `OpenApiContractTest` | verde; snapshot regenerado |
| `FronteiraDeModulosTest` | 2 testes, 0 falhas |
| Frontend | 130 testes em 29 arquivos, typecheck e lint verdes |

O snapshot OpenAPI mudou muito além do endpoint novo: ele estava defasado em
relação a todo o trabalho não commitado da árvore.

## Implantação da V22 — autorizada e executada

A restrição inicial proibia mexer no banco. Depois, com autorização explícita
("suba o v22, só não quero a quebra do banco de dados"), a migration foi
aplicada. O pedido não era só "aplique": era **aplique sem quebrar**, e o
procedimento seguiu essa leitura.

### Antes de encostar no banco

1. **Leitura da migration.** V22 é puramente aditiva: cria `audit_event`, três
   índices, uma política de RLS, uma função e um gatilho. Nenhum `DROP`,
   `TRUNCATE`, `DELETE` ou `ALTER` destrutivo. Os únicos `REVOKE` recaem sobre
   objetos que a própria migration cria.
2. **Checagem de privilégio.** O `ALTER DEFAULT PRIVILEGES` da V9 concede
   `arwd` ao runtime em tabela nova; a V22 revoga `UPDATE` e `DELETE`, deixando
   inserir e consultar. Ou seja, o runtime não ficaria sem acesso à trilha —
   que teria quebrado toda ação auditada, porque auditoria de credencial e
   configuração é fail-closed.
3. **Backup completo**, formato custom, 288 KB, fora do repositório.
4. **Linha de base** de sete tabelas.
5. **`MigracaoDeAtualizacaoTest` verde**, provando o caminho de atualização em
   contêiner descartável antes do banco real.

### Resultado

| Verificação | Resultado |
|---|---|
| Flyway | `Successfully applied 2 migrations, now at version v22` |
| App | healthy em ~35 s |
| Dados | 2 tenants, 3 usuários, 4 canais, 3 conversas, 32 mensagens, 1 contato, 18 eventos — **idênticos à linha de base** |
| `audit_event` no runtime | `INSERT, SELECT` e nada mais |
| Evolution | `pnp-teste` permaneceu `open`; canal seguiu `HEALTHY` |
| Endpoint novo | `/api/canais/{id}/pareamento` servido e protegido |
| Log do app | zero linhas de erro |

O backup fica no diretório temporário da sessão e **contém dado pessoal**: não
entra no repositório, e convém removê-lo quando não for mais necessário.

## Pendências

- Canal órfão `teste` continua em `ERROR`, por decisão.
- O pareamento está implantado, mas ainda não foi exercitado por uma pessoa
  pela tela: a prova é de teste de contrato mais a rota servida e protegida.
  O primeiro uso real fecha isso.
- Reexecutar a suíte backend integral segue pendente do Prompt 17.
