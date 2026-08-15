# ADR-0018 — Relatórios versionados com exportação cifrada e reautorizada

- Status: aceito
- Data: 2026-08-15
- Contexto relacionado: [[ADR-0008]], [[ADR-0013]], [[ADR-0016]], [[ADR-0017]]

## Contexto

O dashboard já compunha números pelas APIs públicas dos módulos, mas a fórmula
era um contrato implícito no controller. Não havia catálogo versionado, job de
exportação, armazenamento privado nem revalidação de acesso fora da requisição.

Um arquivo exportado é um segundo canal de leitura: autorizar apenas o pedido
permitiria que uma pessoa revogada recebesse ou baixasse o resultado depois.

## Decisão

### Métrica tem contrato explícito

`report_metric_definition` é catálogo estrutural somente leitura no runtime.
Cada versão declara código, fórmula, granularidade, fonte, timezone, unidade e
moeda quando o valor for monetário.

O primeiro relatório é `OVERVIEW_V1`, uma fotografia agregada do tenant. Os
valores monetários continuam em menor unidade e usam `BRL`, que já é o contrato
do dashboard brasileiro; “mensagens hoje” declara `America/Sao_Paulo`. Isso não
é política de cobrança e não cria preço.

Dashboard e exportação usam `ReportSnapshotService`, evitando duas fórmulas
para o mesmo indicador. Nova semântica recebe nova versão, nunca reescreve a
definição que explica um arquivo antigo.

### Exportação é job tenant-scoped e idempotente

`report_export_job` usa RLS `ENABLE + FORCE`. A chave é única por tenant,
solicitante e `Idempotency-Key`; repetição converge para o mesmo job. A reserva
cross-tenant é `SECURITY DEFINER` estreita e devolve somente ids. O worker volta
ao `TenantContext` antes de consultar o job ou produzir métricas.

O primeiro formato é CSV e o primeiro dataset contém só agregados. Não há nome,
telefone, e-mail, mensagem ou outro campo pessoal para mascarar. Adicionar
dataset detalhado exigirá uma definição de campos e permissões própria.

O job é limitado por lote, concorrência por tenant, tentativas, linhas, colunas
e 5 MiB. CSV neutraliza células iniciadas por `=`, `+`, `-` ou `@`, inclusive
após espaços, e escapa aspas.

### Acesso é revalidado três vezes

`reports.read` com alcance `TENANT` é exigido:

1. ao solicitar;
2. pelo worker imediatamente antes da captura;
3. ao criar a URL e novamente no download.

O job só pode ser consultado e baixado por quem o solicitou. Revogação posterior
ao pedido produz `DENIED`; revogação posterior à geração impede URL/download.
Entitlement e pagamento não substituem essa autorização.

### Arquivo privado, cifrado e temporário

O CSV é cifrado com AES-256-GCM e AAD `tenant + export`, antes de ir para volume
fora do web root. A chave é exclusiva. O link HMAC usa outra chave, vale no
máximo cinco minutos e ainda exige sessão e autorização válidas.

O artefato expira em 24 horas por configuração. A expiração sempre invalida o
link. Legal hold de `REPORT_EXPORT` ou do tenant preserva somente o ciphertext;
encerrado o hold, o worker remove o arquivo e registra `purged_at`.

Pedido, conclusão, cancelamento e download entram na auditoria append-only sem
conteúdo do arquivo, chave idempotente ou caminho de storage.

## Consequências

- Resultado exportado reconcilia com a mesma fotografia usada pelo dashboard.
- RLS e reautorização cobrem requisição, processamento e entrega.
- O storage local atende a implantação de uma instância atual. Escala horizontal
  exigirá storage privado compartilhado; essa abstração nasce quando houver o
  segundo backend concreto, não antes.
- Exportações detalhadas ainda não existem e não herdam permissões por acidente.

## Alternativas descartadas

**Gerar CSV síncrono no controller.** Não permite cancelamento, limite de
concorrência nem revalidação no momento da execução.

**Link permanente para arquivo em diretório público.** Transforma conhecimento
da URL em autorização e mantém acesso após revogação.

**Consultar tabelas dos outros módulos por SQL no módulo report.** Burlaria a
fronteira do Modulith e criaria contratos invisíveis de schema.

**Exportar contatos já no primeiro dataset.** Exigiria decidir perfil de campos,
mascaramento e recorte por responsável; agregados entregam valor sem antecipar
essa decisão de privacidade.

## Gatilhos de revisão

- primeiro dataset com registro ou dado pessoal;
- segundo formato de arquivo;
- execução em mais de uma instância;
- necessidade de storage de objeto compartilhado;
- nova moeda, timezone ou fórmula de indicador.
