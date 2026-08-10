# Sessão 2026-08-10 — Prompt 18, LGPD, retenção e direitos do titular

- Branch: `main`
- Ambiente: Windows 11, JDK 25.0.4, Docker Desktop, Testcontainers
- Suíte final: **212 testes, 0 falhas**

## Como o prompt foi executado

O Prompt 18 tem um protocolo que impede executá-lo direto: *"decisão jurídica
permanece fora do código"*. Foi feito em duas partes.

**Parte 1 — inventário.** Levantamento factual do que existe: 39 tabelas, 12
categorias de dado pessoal, duas com retenção implementada, três destinos de
transferência. As colunas de hipótese legal, prazo e papel ficaram `A VALIDAR`
— recusa deliberada, não lacuna.

**Parte 2 — mecanismo.** Com autorização explícita para implementar, todo o
maquinário foi construído com os prazos como configuração, desligados por
padrão.

## Os quatro achados

### `LGPD-001` — texto de cliente fora do isolamento — corrigido

`MensagemRecebidaEvent` declarava `String texto`, e o Modulith serializa todo
evento em `event_publication` — a única das 39 tabelas sem RLS, sem retenção e
com acesso total do runtime.

Ninguém lia o campo: o único consumidor já dizia, no próprio comentário, que
não copiava texto para o motor de automações. Era peso morto serializado
indefinidamente — o pior tipo de dado retido, o que ninguém usa e ninguém
lembra que existe.

Três frentes: campo removido; `completion-mode: delete`, para publicação
concluída deixar de virar linha permanente; e expurgo das 2334 concluídas
acumuladas, 1119 com texto de cliente, com backup direcionado antes.

`PrivacidadeDeEventosTest` proíbe texto livre em **qualquer** evento de `api`,
não só o campo que falhou — acrescentar `String` a um evento é o gesto natural
de quem precisa de contexto, então a barreira precisa estar no gesto.

### `LGPD-002` — retenção — mecanismo pronto, prazos pendentes

V23 traz legal hold sob RLS e seis funções de expurgo. **Nenhuma chama
`now()`**: o corte é parâmetro.

Isso resolve duas coisas de uma vez. Torna o aceite verificável — "relógio
controlado prova retenção" só é demonstrável se o teste escolher o agora — e
impede ligar o expurgo sem escolher conscientemente a data.

Legal hold vive no banco, não na configuração, porque precisa valer para
qualquer caminho que apague: worker, pedido de titular ou operação manual. Um
hold que dependesse de a aplicação lembrar de consultar não seria hold.

Nove testes cobrem os dois lados: hold que preserva, e hold que **não** protege
além do escopo autorizado. Hold amplo demais é tão defeituoso quanto um que
falha.

Contato é anonimizado, não apagado: oportunidade, tarefa e conversa o
referenciam.

**Prazos continuam `A VALIDAR`; o worker nasce desligado e zero desativa cada
categoria.**

### `LGPD-003` — direitos do titular — corrigido

`POST /api/privacidade/titulares/{id}/exportacao` devolve o dado **dizendo de
onde cada seção veio**: tabela, finalidade e prazo — ou a ausência dele, dita
com todas as letras. Uma lista sem origem obriga o titular a confiar; com
origem, ele confere. Foi o ponto que o pedido destacou.

`POST .../anonimizacao` remove o dado pessoal e preserva a relação. Sob legal
hold, **recusa com motivo e instrução**. Devolver 200 sem apagar faria o
titular sair acreditando que o dado foi removido — o aceite chama isso de
"falha silenciosa" e a proíbe.

Permissão `privacy.manage`, separada de `organization.manage`: responder a um
direito não deveria exigir poder de administrar papéis e canais.

Exportação não inclui dado de terceiro — a identidade do atendente aparece como
função, porque ele é outro titular.

### `LGPD-004` — backup — documentado

`politica-de-backup-e-replica.md` diz o incômodo em vez de contorná-lo:
**expurgo que não alcança backup não é expurgo**. Um backup de trinta dias
atrás ainda contém quem exerceu eliminação ontem.

Não há rotina de backup. Os dois dumps avulsos desta sequência são exemplo vivo
do problema, e o documento fixa a regra enquanto não houver rotina, mais o que
o Prompt 23 precisa cumprir — inclusive o procedimento pós-restauração, sem o
qual restaurar ressuscita dado que o titular mandou apagar.

## Falhas encontradas no caminho

**Guardas que reprovaram como deviam.** `BancoSegurancaTest` acusou tabela e
funções privilegiadas novas; o detector de deriva de schema devolveu 503 no
readiness. Ambos foram atualizados explicitamente, nunca afrouxados.

**Auditoria fail-closed recusando.** Os endpoints de privacidade davam 500 com
`AuditUnavailableException`. Causa: eles não eram transacionais, e sem
transação o `set_config` local do tenant não sobrevive até o `INSERT`, então o
RLS recusa a escrita na trilha. Corrigido com `@Transactional`, que também é
semanticamente certo — auditoria e operação devem confirmar juntas.

**Dois construtores sem `@Autowired`** impediam o contexto de subir.

## Pendências

- **Prazos por categoria**: decisão de negócio, ainda ausente. Sem ela o
  expurgo não liga.
- **Papel por finalidade, hipótese legal e quem declara legal hold**: pedem
  validação competente.
- **V23 aplicada** em 2026-08-10; ver a seção final.
- Backup e réplica: documentados, não implementados. Prompt 23.
- `LGPD-005`: revogar `DELETE` do runtime em `legal_hold`.

## Aplicação da V23 — 2026-08-10

Mesmo procedimento da V22: leitura da migration, backup, linha de base, e só
então aplicar.

**Verificação prévia.** Um `grep` cru acusou seis comandos destrutivos, e a
conferência mostrou que todos os `DELETE FROM` estão **dentro** dos corpos das
funções de expurgo — que só apagam com corte explícito. Fora de função:
**zero**. A distinção importa, e conferi em vez de assumir.

**Backup** de 5,0 MB antes de qualquer coisa.

| Verificação | Resultado |
|---|---|
| Flyway | `now at version v23` |
| App | healthy em ~40 s |
| Dados | 2 tenants, 3 usuários, 1 contato, 20 conversas, 1324 mensagens, 5 canais, 1324 eventos de uso, 5 de auditoria — **idênticos à linha de base** |
| `legal_hold` | RLS `true/true` |
| `contact.anonymized_at` | criada |
| Funções de expurgo | 8, todas executáveis pelo runtime |
| **Worker de retenção** | **não subiu** — nenhuma menção no log, nenhum expurgo executado |
| Endpoints de privacidade | servidos |
| Log do app | zero erros |

O worker não ter subido é o resultado mais importante: nenhum prazo foi
decidido, e o `@ConditionalOnProperty` impediu o componente de existir. O
expurgo está pronto e inerte.

### Achado da verificação — `LGPD-005`

`legal_hold` concede `INSERT, SELECT, UPDATE, DELETE` ao runtime, herdado do
`ALTER DEFAULT PRIVILEGES` da V9. O mesmo papel que executa o expurgo pode
remover a trava que deveria impedi-lo.

O contraste denuncia: `audit_event` teve `UPDATE` e `DELETE` revogados na V22
justamente por ser superfície de controle. `legal_hold` é da mesma natureza e
não recebeu o mesmo cuidado.

Não é exploração hoje — nenhum endpoint apaga hold, e o desenho pretendido é
revogação lógica por `deleted_at`. O risco é de caminho futuro: quem escrever a
gestão de hold vai encontrar `DELETE` disponível.

## Correção do `LGPD-005` — V24

O achado da verificação da V23 foi corrigido no mesmo dia.

**Duas camadas, e a segunda existe porque a primeira é reversível.**

1. **Privilégio.** `DELETE` revogado do runtime e `UPDATE` restrito por coluna
   a `valid_until`, `deleted_at`, `updated_at` e `updated_by`.
2. **Gatilho `legal_hold_imutavel`.** Vale para qualquer papel, inclusive o de
   migração. Privilégio é concedido e revogado; gatilho é propriedade da
   tabela, e sobrevive a um `GRANT` distraído feito seis meses depois.

**Duas formas de neutralizar um hold, ambas fechadas.** Apagar o registro leva
junto a prova de por que o dado foi retido. Reescrever `target_type` seria
pior: o hold continuaria na tabela, aparentemente intacto, e deixaria de cobrir
o que cobria — falha que não aparece em nenhuma listagem. Escopo, motivo e
origem passaram a ser imutáveis.

**Encerrar continua possível**, por `deleted_at` ou `valid_until`. Um hold que
ninguém consegue encerrar congelaria o dado para sempre, o que também viola o
titular — e há teste fechando esse ciclo: hold encerrado deixa de bloquear.

`TRUNCATE` não dispara gatilho de linha, então a limpeza de teste continua
funcionando. Conferi isso antes de escrever a migration, não depois.

### Verificação no banco vivo

| Item | Resultado |
|---|---|
| Flyway | `now at version v24` |
| Privilégios de tabela do runtime | `INSERT, SELECT` — nada além |
| Colunas com `UPDATE` | `deleted_at, updated_at, updated_by, valid_until` |
| Gatilho | `legal_hold_imutavel` ativo |
| Dados | contato intacto; mensagens subiram de 1535 para 1607, porque o WhatsApp seguiu recebendo durante o deploy — crescimento, não perda |
| Suíte | 218 testes, 0 falhas |

### Nota operacional

O Docker Desktop caiu pela terceira vez nesta sequência, no meio do trabalho.
A pilha voltou incompleta e o `evolution-api` entrou em laço de reinício por
uma corrida: tentou migrar antes do próprio PostgreSQL ficar pronto. Estabilizou
sozinho depois que o banco ficou saudável. É mais uma ocorrência do `SEC-012`.
