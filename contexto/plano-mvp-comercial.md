# Plano de implementação — MVP comercial (12 itens)

Levantado em 2026-08-10 contra o código real: 56 rotas, 19 módulos, banco na
V24, 218 testes verdes.

Este documento diz **o que fazer, como, e por que assim**. Onde há armadilha
conhecida, ela está escrita — vale mais que o passo a passo.

---

## 0. Antes de começar: três decisões que travam o resto

Nenhuma delas é técnica, e todas mudam o desenho. Adiar qualquer uma custa
retrabalho, não tempo de espera.

### 0.1 Evolution ou WhatsApp Cloud API oficial

O WhatsApp que funciona hoje é a **Evolution**, uma ponte de sessão WhatsApp
Web. Ela está documentada no projeto como laboratório. As consequências não são
de opinião:

- Disparo em massa por sessão não oficial é o caminho mais curto para o número
  ser banido — e o número banido leva junto o histórico de atendimento.
- Não há template aprovado, então não existe forma legítima de iniciar conversa
  fora da janela de 24 horas.
- Instagram (item 10) e Conversions API (item 3) só existem no ecossistema
  oficial da Meta.

Os itens **2, 3, 7, 10 e 12 dependem desta escolha**. O plano abaixo assume a
migração para a Cloud API oficial, e marca o que muda se a decisão for ficar na
Evolution.

### 0.2 Base legal para enviar lead à Meta

O item 3 envia dado de pessoa para a Meta. Isso é transferência a terceiro, e o
inventário LGPD do projeto já trata transferência como categoria própria. Antes
de implementar é preciso: hipótese legal definida, registro de consentimento por
titular quando for o caso, e a Meta acrescentada ao inventário.

Implementar sem isso produz um sistema que vaza dado com aparência de
integração.

### 0.3 Hierarquia exige destravar o ADR-0008

O item 6 pede visibilidade por posição na hierarquia. Hoje o alcance `UNIT`
**não decide sobre registro de domínio**: nenhuma tabela de domínio declara
unidade, e o ADR-0008 registra a recusa deliberada de inferi-la.

Não dá para entregar o item 6 sem migration aditiva de `unit_id` e um ADR novo
substituindo o 0008. Isso está detalhado na Fase 4.

---

## 1. Fluxo de execução

A ordem não é a da lista: ela segue dependência real. Os números entre
parênteses são os itens do pedido.

```
FASE 1 — Base do funil e do lead          (1, 2)
   │        pipeline editável + campos do lead
   ├──────────────► FASE 2 — Ligação e história   (4, 8)
   │                   conversa↔oportunidade + linha do tempo
   │                          │
   │                          ├──────► FASE 5 — Métrica  (9, 5)
   │                          │           KPI configurável + dashboard
   │                          │
   │                          └──────► FASE 4 — Hierarquia (6)
   │                                      unit_id + ADR novo
   │
FASE 3 — Canal oficial Meta               (7, 10-Instagram)
   │        WhatsApp Cloud API + Instagram
   ├──────────────► (3) Conversions API
   └──────────────► FASE 6 — Disparo em massa (12)
                       templates + opt-in + pacing

FASE 7 — Independentes                    (10-e-mail, 11)
           e-mail e Google Agenda/Meet
```

**Por que esta ordem.** A Fase 2 é a chave: a linha do tempo do lead alimenta
métrica (5, 9) e hierarquia (6). Construir dashboard configurável antes de ter
o evento do lead produz gráfico sobre dado que não existe. E a Fase 3 destrava
três itens de uma vez, então adiá-la empurra 3, 10 e 12 juntos.

---

## 2. Regras que valem para tudo

O projeto já tem invariantes provadas por teste. Todo código novo herda estas,
sem exceção — quebrar uma delas reprova o build, e isso é proposital.

**Banco.** Toda tabela nova: `tenant_id NOT NULL REFERENCES tenant(id)`, RLS
`ENABLE + FORCE` com política `tenant_id = current_tenant_id()`, chave composta
`UNIQUE (tenant_id, id)` para permitir FK composta, `created_at`/`updated_at`/
`created_by`/`updated_by`/`deleted_at`. Referência entre tabelas usa FK
composta `(tenant_id, x_id)` — é o que impede um registro apontar para outro
tenant.

**Migration.** Aditiva, imutável depois de aplicada, e cada uma exige atualizar
três guardas que vão reprovar de propósito:
`BancoSegurancaTest` (tabela e funções privilegiadas novas),
`MigracaoDeAtualizacaoTest` (contagem e versão),
`app.schema.expected-version` mais `ProductionConfigurationTest`.

**Autorização.** Endpoint novo declara permissão no catálogo `Permissao`, e
chama `autorizacao.exigirNoTenant(...)` ou `exigirSobreRegistro(...)`. Recurso
coletivo exige alcance `TENANT`; recurso com responsável aceita `PROPRIO` e o
recorte entra **na consulta**, nunca sobre a página.

**Auditoria.** Mudança de credencial, configuração, papel ou exportação é
auditada, e a trilha guarda metadado — nunca conteúdo.

**Evento de aplicação nunca carrega texto.** `PrivacidadeDeEventosTest` reprova
qualquer `String` livre em evento de `api`. Isso não é preciosismo: o Modulith
serializa evento em `event_publication`, a única tabela sem RLS.

**Contrato.** Rota nova exige regenerar `openapi.json` e os tipos TypeScript.
Só o adaptador do frontend importa o contrato; as páginas usam modelos próprios.

---

## FASE 1 — Base do funil e do lead

### Item 1 — Pipeline e etapas editáveis

**O que existe.** `pipeline` e `pipeline_stage` com `position`, `is_won`,
`is_lost`. Card completo: criar, editar, mover, excluir. `/api/funis` é **só
GET** — o funil vem do preset de segmento e ninguém consegue mudá-lo.

**O que falta.** CRUD de funil e etapa, e reordenação.

**Migration.** Nenhuma coluna nova é necessária. Só um índice para a
reordenação ficar barata e uma restrição que o domínio já assume:

```sql
-- Duas etapas na mesma posição tornam a ordem do kanban indeterminada:
-- o usuário arrasta, solta, e a tela mostra outra coisa a cada carregamento.
CREATE UNIQUE INDEX pipeline_stage_posicao_unica
    ON pipeline_stage (tenant_id, pipeline_id, position)
    WHERE deleted_at IS NULL;
```

**API.**

```
POST   /api/funis                          criar funil
PUT    /api/funis/{id}                     renomear, marcar padrão
DELETE /api/funis/{id}                     exclusão lógica
POST   /api/funis/{id}/etapas              criar etapa
PUT    /api/funis/{id}/etapas/{etapaId}    renomear, marcar ganho/perda
DELETE /api/funis/{id}/etapas/{etapaId}    exclusão lógica
PUT    /api/funis/{id}/etapas/ordem        reordenar em lote
```

**Reordenação: faça em lote, não uma a uma.** Arrastar um card produz uma nova
ordem inteira. Mandar seis chamadas de `PUT posicao` gera estados intermediários
que violam a unicidade e deixam a tela inconsistente se uma falhar no meio.

```java
@PutMapping("/funis/{funilId}/etapas/ordem")
@Transactional
ResponseEntity<List<EtapaResponse>> reordenar(
        @PathVariable UUID funilId,
        @Valid @RequestBody OrdemRequest requisicao) {
    autorizacao.exigirNoTenant(Permissao.DEALS_WRITE);

    // A lista precisa ser exatamente as etapas do funil: nem a mais, nem a
    // menos. Aceitar lista parcial deixaria as ausentes com posição antiga e
    // colidindo com as novas.
    List<UUID> atuais = etapas.idsDoFunil(TenantContext.obrigatorio(), funilId);
    if (!Set.copyOf(atuais).equals(Set.copyOf(requisicao.etapaIds()))) {
        throw new OrdemIncompletaException(
                "Informe todas as etapas do funil, na ordem desejada.");
    }

    // Posição negativa primeiro para não colidir com as antigas durante a
    // renumeração. Sem isso, o índice único reprova no meio do caminho.
    etapas.deslocarParaNegativo(TenantContext.obrigatorio(), funilId);
    for (int i = 0; i < requisicao.etapaIds().size(); i++) {
        etapas.definirPosicao(TenantContext.obrigatorio(),
                requisicao.etapaIds().get(i), i);
    }
    return ResponseEntity.ok(etapasDe(funilId));
}
```

**Armadilhas que já custaram caro em outros CRMs:**

1. **Excluir etapa com oportunidade dentro.** Recuse com motivo, ou exija
   destino. Nunca apague em cascata: some com o negócio, não com a etapa.
   ```java
   long presas = oportunidades.contarNaEtapa(tenantId, etapaId);
   if (presas > 0 && requisicao.destinoId() == null) {
       throw new EtapaOcupadaException(
           presas + " oportunidade(s) nesta etapa. Informe a etapa de destino.");
   }
   ```
2. **Remover a última etapa de ganho ou de perda.** `DealEntity.moverPara`
   decide status por `is_won`/`is_lost`. Um funil sem etapa de ganho torna
   impossível fechar negócio, e o erro só aparece semanas depois.
3. **Excluir o funil padrão.** `FunilPadraoService` cria o padrão na primeira
   chamada de `/api/funis`. Excluir o padrão sem eleger outro faz o serviço
   recriar um funil vazio no próximo acesso.

**Frontend.** `PipelinesPage.tsx` já tem arrastar-e-soltar de card. Acrescentar
modo de edição de colunas: renomear inline, botão de nova etapa, e arrastar a
própria coluna. Envie a ordem inteira no `drop`, com atualização otimista e
reversão se o servidor recusar.

**Testes.** Reordenar mantém unicidade; excluir etapa ocupada é recusado com
motivo; remover a última etapa de ganho é recusado; e o teste de concorrência —
dois reordenamentos simultâneos não produzem posição duplicada.

---

### Item 2 — Dados do lead na Inbox

**O que existe.** Inbox real, com WhatsApp provado ponta a ponta. `contact` tem
`name`, `email`, `phone`, `company_name`, `notes`, `contact_kind`.

**O que falta.** Cidade, estado, e um painel do contato dentro da conversa.

**A decisão de modelagem importa mais que a tela.** O pedido diz "cidade, estado
**e etc**". Há duas formas de atender o "etc":

| Abordagem | A favor | Contra |
|---|---|---|
| `JSONB` livre | rápido, flexível | **inviabiliza o inventário LGPD**: ninguém sabe que dado pessoal existe lá dentro; e não dá para indexar nem validar |
| Campo customizado tipado | inventariável, validável, indexável | uma tabela a mais |

**Recomendação: colunas fixas para o que é universal, campo customizado tipado
para o resto.** O projeto acabou de fechar um inventário de tratamento que
depende de saber onde cada dado pessoal está. Um `JSONB` livre destrói isso em
uma migration.

```sql
-- V25: campos de endereço e definição de campo customizado.

ALTER TABLE contact
    ADD COLUMN city  text,
    ADD COLUMN state text;

-- Estado como sigla de duas letras: texto livre vira "SP", "sp", "São Paulo"
-- e "Sao Paulo" na mesma coluna, e nenhum relatório por estado funciona.
ALTER TABLE contact
    ADD CONSTRAINT contact_uf_valida
    CHECK (state IS NULL OR state ~ '^[A-Z]{2}$');

CREATE TABLE contact_field_definition
(
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant (id),
    code        text NOT NULL,
    label       text NOT NULL,
    -- Tipo fechado: o valor é validado na escrita e sabe-se ler na leitura.
    kind        text NOT NULL,
    required    boolean NOT NULL DEFAULT false,
    position    integer NOT NULL DEFAULT 0,
    -- Declara se o campo guarda dado pessoal. É o que mantém o inventário
    -- honesto quando o cliente cria um campo chamado "obs" e escreve o CPF.
    personal_data boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT contact_field_kind_valido
        CHECK (kind IN ('TEXT','NUMBER','DATE','BOOLEAN','SELECT')),
    CONSTRAINT contact_field_code_formato
        CHECK (code ~ '^[a-z][a-z0-9_]{1,40}$'),
    CONSTRAINT contact_field_code_unico UNIQUE (tenant_id, code),
    CONSTRAINT contact_field_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE TABLE contact_field_value
(
    tenant_id     uuid NOT NULL,
    contact_id    uuid NOT NULL,
    definition_id uuid NOT NULL,
    value_text    text,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    PRIMARY KEY (tenant_id, contact_id, definition_id),
    FOREIGN KEY (tenant_id, contact_id)    REFERENCES contact (tenant_id, id),
    FOREIGN KEY (tenant_id, definition_id)
        REFERENCES contact_field_definition (tenant_id, id)
);
```

Depois de criar, **acrescente as duas tabelas ao inventário de tratamento** e a
`contact_field_value` à retenção do `LGPD-002` — senão o campo customizado
nasce fora de toda política.

**API.**

```
GET,POST /api/contatos/campos              definições
PUT,DELETE /api/contatos/campos/{id}
PUT      /api/contatos/{id}/campos         valores em lote
```

**Frontend.** Painel lateral na conversa, com edição inline. Dois cuidados:

- **Salvar no `blur`, não a cada tecla.** Atendente digitando telefone geraria
  uma escrita por dígito, e o `updated_at` viraria ruído no histórico.
- **Versão otimista.** `contact` não tem `version` ainda; acrescente, ou aceite
  o último a escrever. Dois atendentes na mesma conversa é o caso comum, não a
  exceção.

**Se ficar na Evolution:** funciona igual. O painel não depende do canal.

---

## FASE 2 — Ligação e história

### Item 4 — Kanban dentro do chat

**O que falta.** `conversation` não referencia `deal`.

```sql
-- V26
ALTER TABLE conversation ADD COLUMN deal_id uuid;
ALTER TABLE conversation
    ADD CONSTRAINT conversation_deal_mesmo_tenant
    FOREIGN KEY (tenant_id, deal_id) REFERENCES deal (tenant_id, id);
CREATE INDEX conversation_por_deal
    ON conversation (tenant_id, deal_id) WHERE deleted_at IS NULL;
```

**Uma conversa aponta para uma oportunidade, e não o contrário.** O mesmo lead
pode voltar meses depois com outro negócio; a conversa é do momento, a
oportunidade é do ciclo. Se um dia precisar de várias, o histórico de movimento
já estará em `lead_event` e a migração é aditiva.

**API.** Não crie endpoint novo de movimentação. Reuse
`POST /api/oportunidades/{id}/mover` — a regra de negócio, a autorização e o
evento de tempo real já estão lá e testados. Um segundo caminho de movimentação
significa duas regras que divergem no primeiro ajuste.

Acrescente só a ligação:

```
PUT    /api/conversas/{id}/oportunidade    vincular
DELETE /api/conversas/{id}/oportunidade    desvincular
```

**Frontend.** No cabeçalho da conversa, mostre a etapa atual e um seletor. Ao
mover, chame `mover` e invalide as duas queries — inbox e kanban. Como o mover
já publica evento de tempo real, o quadro de quem estiver com o kanban aberto
atualiza sozinho.

---

### Item 8 — Histórico do lead e tempo de venda

Este é o item que sustenta 5, 6 e 9. Vale fazer bem.

**Metade já existe.** `deal` tem `created_at`, `closed_at` e `status`. Tempo de
venda de negócio ganho é `closed_at - created_at` — sem tabela nova, sem
trabalho. O que falta é a **linha do tempo**.

```sql
-- V27: linha do tempo do lead, append-only.
CREATE TABLE lead_event
(
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL REFERENCES tenant (id),
    contact_id  uuid NOT NULL,
    deal_id     uuid,
    occurred_at timestamptz NOT NULL,
    event_type  text NOT NULL,
    -- Identificadores e códigos. NUNCA texto de mensagem: a timeline é lida
    -- por muita gente, e duplicar conteúdo aqui criaria uma segunda cópia
    -- fora do alcance do expurgo.
    actor_user_id uuid,
    from_stage_id uuid,
    to_stage_id   uuid,
    reference_id  uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT lead_event_tipo_valido CHECK (event_type IN (
        'LEAD_CREATED','MESSAGE_RECEIVED','MESSAGE_SENT','STAGE_CHANGED',
        'OWNER_CHANGED','TASK_COMPLETED','DEAL_WON','DEAL_LOST',
        'FIELD_UPDATED','CHANNEL_LINKED')),
    FOREIGN KEY (tenant_id, contact_id) REFERENCES contact (tenant_id, id),
    CONSTRAINT lead_event_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE INDEX lead_event_linha_do_tempo
    ON lead_event (tenant_id, contact_id, occurred_at DESC, id DESC);

ALTER TABLE lead_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE lead_event FORCE ROW LEVEL SECURITY;
CREATE POLICY lead_event_isolamento ON lead_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- Mesma lição do audit_event: linha do tempo que pode ser reescrita não é
-- linha do tempo. O expurgo por retenção é o único caminho de remoção.
REVOKE UPDATE, DELETE ON lead_event FROM "${runtime_role}";
```

**Como alimentar.** Escute os eventos de domínio que já existem
(`MensagemRecebidaEvent`, e os que faltarem crie), e grave o `lead_event` na
mesma transação do fato. Gravar depois, por worker, produz linha do tempo com
buraco quando o worker falha — e ninguém confia em histórico furado.

**Não invente um segundo mecanismo de auditoria.** `audit_event` responde "quem
mexeu na configuração"; `lead_event` responde "o que aconteceu com este lead".
São públicos diferentes: o primeiro é do encarregado, o segundo é do vendedor.
Misturar produz uma tela que não serve a nenhum dos dois.

**Métricas derivadas** (sem tabela nova, tudo consulta):

```sql
-- Tempo médio da chegada até a conversão, por período.
SELECT avg(closed_at - created_at) AS ciclo_medio
  FROM deal
 WHERE tenant_id = current_tenant_id()
   AND status = 'WON' AND deleted_at IS NULL
   AND closed_at >= :inicio AND closed_at < :fim;

-- Tempo em cada etapa: diferença entre STAGE_CHANGED consecutivos.
SELECT to_stage_id,
       avg(lead(occurred_at) OVER w - occurred_at) AS permanencia
  FROM lead_event
 WHERE tenant_id = current_tenant_id() AND event_type = 'STAGE_CHANGED'
WINDOW w AS (PARTITION BY deal_id ORDER BY occurred_at)
GROUP BY to_stage_id;
```

**API.** `GET /api/contatos/{id}/timeline`, paginada por cursor keyset — a V19
já estabeleceu o padrão, e `OFFSET` numa timeline longa degrada e duplica
linhas quando chega evento novo.

**Retenção.** Acrescente `lead_event` ao `LGPD-002` e ao inventário. Timeline é
dado pessoal, e nasce sem prazo se ninguém disser o contrário.

---

## FASE 3 — Canal oficial da Meta

### Item 7 — WhatsApp Cloud API + Instagram

**A boa notícia.** A arquitetura já está pronta: existe a porta
`ChannelAdapter`, o padrão de webhook que **persiste antes de confirmar**, a
verificação de assinatura em tempo constante, o cofre de credenciais cifrado e
a fila de saída com lease e backoff. `WHATSAPP_CLOUD` e `INSTAGRAM` já são
valores do enum `TipoCanal` — falta o adaptador.

**Pré-requisitos fora do código**, e são o caminho crítico: app na Meta,
verificação do negócio, número dedicado, token permanente de sistema, e os
templates submetidos à aprovação. Isso leva dias a semanas e **não depende de
programação** — comece por aqui, em paralelo.

**Webhook.** A Meta assina com HMAC-SHA256 sobre o corpo cru, em
`X-Hub-Signature-256: sha256=...`.

```java
// O corpo precisa ser os BYTES ORIGINAIS. Serializar e re-serializar muda
// espaçamento e ordem de chave, e a assinatura deixa de bater — é a causa
// mais comum de "webhook da Meta não funciona".
@PostMapping(value = "/webhooks/meta/{channelConnectionId}",
             consumes = MediaType.APPLICATION_JSON_VALUE)
ResponseEntity<Void> receber(@PathVariable UUID channelConnectionId,
                             @RequestHeader("X-Hub-Signature-256") String assinatura,
                             @RequestBody byte[] corpoCru) {
    // ... resolve o segredo do canal, calcula o HMAC, compara com
    // MessageDigest.isEqual — nunca com equals, que vaza tempo.
}
```

O `GET` do mesmo caminho responde o desafio de verificação
(`hub.mode`, `hub.verify_token`, `hub.challenge`).

**A janela de 24 horas é regra de negócio, não detalhe.** Fora dela, só
mensagem de template aprovado. O adaptador precisa saber disso e falhar com
erro **permanente**, não temporário — repetir uma mensagem que a política
recusa só queima cota e atrasa a fila.

```java
if (foraDaJanelaDeAtendimento(conversa)) {
    throw EnvioDeMensagemException.permanente(
        "Fora da janela de 24 horas: use um template aprovado.");
}
```

**Instagram** entra pelo mesmo webhook, com `object: "instagram"`. Reaproveite o
tradutor, mudando só o mapeamento de identidade.

**Migration.** Nenhuma tabela nova — `channel_connection` e `channel_credential`
já servem. Acrescente o tipo de credencial `META_ACCESS_TOKEN` e
`META_APP_SECRET` ao enum `TipoCredencial`.

**Se a decisão for ficar na Evolution:** os itens 3, 10-Instagram e 12 saem do
MVP. Não há meio-termo honesto.

---

### Item 3 — Conversions API (rastreio de lead)

**Depende da 0.2.** Sem base legal definida, não implemente.

**O que é.** Envio servidor-a-servidor de eventos de conversão para a Meta, com
PII **hasheada**. É o par server-side do pixel.

**Regras que definem se funciona ou não:**

1. **Normalizar antes de hashear.** E-mail em minúscula sem espaço; telefone só
   dígitos com código do país, sem `+`. Hash SHA-256 do valor normalizado.
   Hashear sem normalizar produz um hash que nunca casa, e o sintoma é
   "integração funciona mas não atribui nada".
2. **`event_id` estável** para deduplicar com o pixel. Use o id da oportunidade
   ou do lead — nunca um UUID novo a cada envio.
3. **Reuse o conector HTTP seguro.** O Prompt 16 já entregou cliente com TLS,
   timeout, teto de resposta, orçamento por tenant e bloqueio de rede privada.
   Não escreva um segundo cliente HTTP.

```java
private static String hashear(String valor) {
    // Normalizar primeiro: "  Joao@Exemplo.COM " e "joao@exemplo.com" precisam
    // produzir o mesmo hash, senão a Meta nunca reconhece a pessoa.
    String normalizado = valor.trim().toLowerCase(Locale.ROOT);
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256")
            .digest(normalizado.getBytes(StandardCharsets.UTF_8)));
}
```

4. **Consentimento por titular.** Grave-o, e não envie quem não consentiu.
   Envio "por padrão" transforma cada lead numa transferência sem base.

---

## FASE 4 — Hierarquia (item 6)

**Está bloqueado por ADR aceito.** O `Alcance` decide `TENANT` e `PROPRIO`;
`UNIT` falha fechado porque nenhuma tabela de domínio declara unidade.

**Trabalho real:**

```sql
-- V28: unidade nos registros de domínio.
ALTER TABLE contact      ADD COLUMN unit_id uuid;
ALTER TABLE deal         ADD COLUMN unit_id uuid;
ALTER TABLE task         ADD COLUMN unit_id uuid;
ALTER TABLE conversation ADD COLUMN unit_id uuid;
-- FK composta em cada uma, contra organizational_unit (tenant_id, id).
```

**A regra de preenchimento é a decisão difícil, e o ADR-0008 já avisou por quê.**
A unidade é gravada **na criação**, a partir da unidade ativa de quem criou, e
é **imutável** depois. Inferi-la na leitura, pela unidade atual do criador,
faria a autorização reescrever o passado toda vez que alguém fosse transferido.

**Backfill do que já existe: assuma nulo e diga isso.** Não há como saber a
unidade de um registro criado antes da coluna existir. `unit_id NULL` significa
"pertence ao tenant, não a uma unidade" e é visível por quem tem alcance de
tenant. Inventar a unidade do dono atual seria inventar história.

Depois: `Alcance.UNIDADE` no enum, resolução em `AutorizacaoService`, recorte
nas consultas de listagem, e **ADR novo referenciando o 0008** — decisão aceita
não se reescreve, ela é substituída.

**Timeline por hierarquia** passa a ser a timeline filtrada pelo alcance: quem
tem `PROPRIO` vê os próprios leads, `UNIDADE` vê a unidade, `TENANT` vê tudo.
Nada de tela nova — é o mesmo endpoint com recorte diferente.

---

## FASE 5 — Métrica (itens 9 e 5)

### Item 9 — KPIs personalizadas

**A armadilha central: não aceite filtro livre.** "KPI personalizada" tenta
naturalmente para um campo onde o usuário escreve condição, e isso é injeção de
SQL com outro nome. O projeto já resolveu esse problema uma vez, no conector
HTTP: a ação recebe **só o `connectorId`**, e método, caminho e corpo pertencem
ao conector aprovado. Faça igual.

```sql
-- V29
CREATE TABLE kpi_definition
(
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenant (id),
    label      text NOT NULL,
    -- Catálogo FECHADO. O usuário escolhe de uma lista; não escreve consulta.
    metric     text NOT NULL,
    -- Filtros tipados, não SQL: {"pipelineId": "...", "ownerUserId": "..."}
    filters    jsonb NOT NULL DEFAULT '{}'::jsonb,
    period     text NOT NULL,
    target_value bigint,
    position   integer NOT NULL DEFAULT 0,
    -- ... colunas de auditoria e RLS como sempre
    CONSTRAINT kpi_metric_valida CHECK (metric IN (
        'DEALS_WON_COUNT','DEALS_WON_VALUE','DEALS_OPEN_COUNT',
        'CONVERSION_RATE','AVG_SALES_CYCLE_DAYS','FIRST_RESPONSE_SLA',
        'MESSAGES_RECEIVED','MESSAGES_SENT','TASKS_OVERDUE')),
    CONSTRAINT kpi_period_valido CHECK (period IN ('DAY','WEEK','MONTH','QUARTER'))
);
```

Cada `metric` mapeia para uma consulta escrita à mão no código. Os filtros são
lidos por chave conhecida e entram como **parâmetro**, nunca concatenados.

```java
private static final Map<String, String> CONSULTAS = Map.of(
    "DEALS_WON_VALUE", """
        SELECT coalesce(sum(value_cents), 0) FROM deal
         WHERE tenant_id = :tenant AND status = 'WON' AND deleted_at IS NULL
           AND closed_at >= :inicio AND closed_at < :fim
           AND (:pipelineId IS NULL OR pipeline_id = :pipelineId)
           AND (:ownerUserId IS NULL OR owner_user_id = :ownerUserId)
        """
    // ... uma por métrica do catálogo
);
```

**Cache.** KPI é caro e muda devagar. Guarde no Redis com chave que inclua
tenant, definição e janela, TTL curto. **A chave precisa do tenant** — cache
compartilhado entre clientes é vazamento que nenhum RLS pega, porque a consulta
nem chega ao banco.

### Item 5 — Dashboard

Já existe com 12 métricas fixas. O trabalho é renderizar as definições do item
9 mantendo as fixas como padrão de quem nunca configurou nada — dashboard vazio
no primeiro acesso é a forma mais rápida de o produto parecer quebrado.

---

## FASE 6 — Disparo em massa (item 12)

**O item de maior risco da lista.** Depende da Fase 3, e errar aqui não gera bug:
gera número banido e conta suspensa.

**Pré-requisitos inegociáveis:** API oficial, templates aprovados, registro de
opt-in por destinatário, e respeito ao tier de qualidade da conta.

```sql
-- V30
CREATE TABLE campaign
(
    id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant (id),
    name text NOT NULL, template_name text NOT NULL,
    channel_connection_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'DRAFT',
    -- Ritmo: mensagens por minuto. Disparo sem pacing é o que dispara o
    -- bloqueio automático do provedor.
    messages_per_minute integer NOT NULL DEFAULT 20,
    scheduled_at timestamptz, started_at timestamptz, finished_at timestamptz,
    CONSTRAINT campaign_status_valido
        CHECK (status IN ('DRAFT','SCHEDULED','RUNNING','PAUSED','DONE','CANCELLED'))
);

CREATE TABLE campaign_recipient
(
    tenant_id uuid NOT NULL, campaign_id uuid NOT NULL, contact_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'PENDING',
    message_id uuid, failure_reason text,
    attempt_count integer NOT NULL DEFAULT 0,
    lease_owner text, lease_until timestamptz,
    PRIMARY KEY (tenant_id, campaign_id, contact_id)
);
```

**Reuse a fila de saída.** `message` já tem lease, backoff, teto de tentativas
e dead letter, tudo testado. A campanha **enfileira**; quem entrega é o worker
que já existe.

**Cinco controles que não são opcionais:**

1. **Opt-out honrado antes de enfileirar**, e também no momento do envio —
   alguém pode pedir descadastro no meio da campanha.
2. **Pacing por janela**, não rajada. Um `sleep` entre lotes é mais barato que
   uma conta suspensa.
3. **Deduplicação**: a chave primária composta impede o mesmo contato receber
   duas vezes na mesma campanha.
4. **Botão de parada** que interrompe de verdade — worker verifica o status da
   campanha a cada lote, não só ao iniciar.
5. **Monitorar a qualidade** reportada pela Meta e pausar sozinho quando cair.

**LGPD.** Cada destinatário precisa de base legal registrada. Campanha para
lista comprada não é problema técnico.

---

## FASE 7 — Independentes

### Item 10 — E-mail

**Use provedor com webhook de entrada** (Postmark, SendGrid, SES+SNS), não IMAP.
Polling de IMAP é frágil, lento e duplica mensagem em reconexão. Webhook cai no
mesmo padrão que o resto do projeto já tem.

**Agrupamento de conversa** vem dos cabeçalhos `Message-ID`, `In-Reply-To` e
`References`. Agrupar por assunto quebra no primeiro "Re: Re: Enc:".

### Item 11 — Google Agenda e Meet

**OAuth 2.0 com acesso offline.** O refresh token vai para o cofre cifrado que
já existe — mesmo padrão de `channel_credential`, chave separada.

**Escopo mínimo:** `calendar.events`. Não peça `calendar` inteiro; o usuário vê
a lista de permissões e desiste.

**Meet** é criado junto do evento, com `conferenceData` e
`conferenceDataVersion=1` — não é uma API separada.

**Sincronia de volta:** canais de notificação do Google expiram e precisam de
renovação agendada. Sem isso, funciona por uma semana e para em silêncio.

---

## 3. Resumo de esforço e risco

| Fase | Itens | Migrations | Risco dominante |
|---|---|---|---|
| 1 | 1, 2 | V25 | baixo — reaproveita tudo |
| 2 | 4, 8 | V26, V27 | baixo — a timeline é o ativo mais reusado |
| 3 | 7, 10-Instagram | nenhuma | **alto, e fora do código**: aprovação da Meta |
| 3b | 3 | nenhuma | **alto**: precisa de base legal antes |
| 4 | 6 | V28 | médio — exige ADR novo e backfill declarado |
| 5 | 9, 5 | V29 | médio — o filtro livre é a armadilha |
| 6 | 12 | V30 | **o maior**: conta banida é irreversível |
| 7 | 10-email, 11 | V31 | médio — OAuth e renovação de canal |

**O caminho crítico não é técnico.** Aprovação da Meta e definição de base legal
levam mais tempo que o código, e travam cinco dos doze itens. Comece por eles
hoje, e use o tempo de espera para as Fases 1, 2 e 4, que não dependem de
ninguém de fora.

## 4. O que eu recomendaria cortar do MVP

Dito com franqueza, porque MVP com doze itens costuma virar zero itens prontos:

- **Item 12 (disparo em massa)** é o que mais pode dar errado e o que menos
  prova o produto. Um CRM que atende bem vale mais que um que dispara mal.
- **Item 6 (hierarquia)** é caro e só faz sentido com várias unidades reais em
  operação. Antes disso, alcance `TENANT` e `PROPRIO` resolvem.
- **Item 11 (Google Agenda)** é isolado e pode entrar depois sem retrabalho.

Cortando os três, sobram nove itens com dependência clara e um caminho crítico
que já começa hoje.
