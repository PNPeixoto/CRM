# Inventário de tratamento de dados

Parte 1 do Prompt 18. **Este documento é factual**: descreve o que o código faz
hoje, levantado do schema em execução e das fontes, em 2026-08-10, com o banco
na V22.

## O que este documento não decide

O protocolo do Prompt 18 é explícito: *"Decisão jurídica permanece fora do
código"* e *"hipótese legal, prazo, legal hold, transferência ou papel
contratual exige validação competente"*.

Por isso as colunas **hipótese legal**, **prazo** e **papel** aparecem como
`A VALIDAR`. Elas não são lacuna de levantamento — são deliberadamente
recusadas. Preencher um prazo de retenção por conta própria produziria um
sistema que apaga dado de cliente em prazo que ninguém validou, com aparência
de conformidade. Isso é pior que não ter política.

O que está preenchido é verificável no código: onde o dado está, o que já
expira, quem consegue ler, e para onde sai.

---

## 1. Categorias de dado pessoal

| Categoria | Onde | Titular | Retenção hoje |
|---|---|---|---|
| Identificação do usuário interno | `app_user` (login, email, full_name) | operador do cliente | **nenhuma** — vive enquanto a conta existir |
| Autenticação | `app_user.password_hash`, `refresh_token`, `password_reset_token`, `mfa_authenticator`, `mfa_enrollment`, `mfa_recovery_code` | operador do cliente | parcial: refresh e reset expiram por desenho; hash de senha e segredo TOTP não têm prazo |
| Contato do cliente final | `contact` (name, email, phone, company_name, notes) | cliente do cliente | **nenhuma** |
| Conversa | `conversation` (external_contact_id, contact_display_name) | cliente do cliente | **nenhuma** |
| Conteúdo de mensagem | `message.text_content`, `message.payload` | cliente do cliente | **nenhuma** |
| Payload bruto de webhook | `inbound_event.payload_ciphertext` | cliente do cliente | **7 dias**, cifrado, com expurgo automático |
| Mídia recebida | `channel_media` + arquivo em disco | cliente do cliente | **30 dias**, com expurgo automático |
| Trilha de auditoria | `audit_event` | operador do cliente | **nenhuma** — append-only por desenho |
| Telemetria de uso | `usage_event` | — (referencia mensagem) | **nenhuma** |
| Stream de tempo real | `realtime_event` | — (só identificadores) | **nenhuma** |
| Diagnóstico de conector | `http_connector_attempt` | variável, conforme o destino configurado | **nenhuma** |
| Log de eventos do Modulith | `event_publication.serialized_event` | — (só identificadores desde 2026-08-10) | concluída é apagada na conclusão — ver `LGPD-001` |

## 2. Onde já existe retenção verificável

Duas categorias têm prazo implementado e expurgo automático. São o modelo a
seguir para as demais.

| Mecanismo | Prazo | Como |
|---|---|---|
| Payload de webhook | 7 dias | `payload_retain_until` com default `now() + interval '7 days'`; worker horário chama `expurgar_payloads_eventos_recebidos`. Preserva hash e metadados de idempotência, apaga só o ciphertext |
| Mídia | 30 dias | `channel_media.retain_until`; worker reserva com lease, valida o caminho contra a raiz configurada, remove o arquivo exato e marca `PURGED` |

Ambos são idempotentes e reservam com lease, o que satisfaz o critério de
aceite *"expurgo idempotente"* — mas **nenhum foi exercitado com relógio
controlado**, que o aceite também exige. Isso fica para a parte 2.

## 3. Transferência a terceiros

| Destino | O que sai | Natureza |
|---|---|---|
| `api.telegram.org` | texto da mensagem enviada, identificador do destinatário | operador de canal, fora do país |
| Evolution API (`127.0.0.1:8081`) | texto da mensagem, número do destinatário | **local**, laboratório; a ponte usa sessão WhatsApp Web e o dado transita pela infraestrutura do WhatsApp |
| Conector HTTP | **definido pelo cliente** — corpo e headers pertencem ao conector aprovado | destino arbitrário aprovado por tenant |

O conector HTTP é o ponto que mais pesa aqui: o destino não é escolhido pela
plataforma, e sim por cada tenant. Qualquer inventário de subprocessadores
depende do que cada cliente configurou, não de uma lista fixa. Isso precisa
constar de qualquer resposta a titular sobre transferência.

## 4. Acesso

O controle é o mesmo de todo o produto e já está provado por teste:

- RLS `ENABLE + FORCE` em **38 das 39 tabelas**; runtime sem `SUPERUSER` e sem
  `BYPASSRLS`.
- Tenant vem só da identidade autenticada, nunca de corpo, query ou header.
- Autorização por ação e por registro; recursos coletivos exigem alcance
  `TENANT`.
- Credencial de canal e segredo de conector são write-only: a API nunca os
  devolve.
- A leitura da própria trilha de auditoria é auditada e exige `audit.read`.

A exceção continua sendo `event_publication`, que não tem RLS. Depois da
correção do `LGPD-001` ela não guarda mais dado de titular, e publicação
concluída é apagada — mas a ausência de isolamento segue valendo para qualquer
evento novo, e é o que o teste de contrato vigia.

## 5. Achados do levantamento

### `LGPD-001` — Texto de mensagem fora do isolamento e de toda retenção — **CORRIGIDO**

- **Severidade:** alta · **Corrigido em 2026-08-10**
- **Onde:** `event_publication`, tabela interna do Spring Modulith.
- **O fato:** `MensagemRecebidaEvent` declarava `String texto` e era
  serializado nessa tabela. No momento da correção havia **2334 publicações
  concluídas**, das quais **1119** carregavam texto de cliente.
- **Por que importa, em três camadas:**
  1. **Sem RLS.** `relrowsecurity` e `relforcerowsecurity` são `f`. É a única
     tabela do schema sem isolamento por tenant — todas as outras 38 têm. Isso
     **permanece verdadeiro**; o que mudou é não haver mais dado de titular ali.
  2. **Acesso total do runtime:** `INSERT, SELECT, UPDATE, DELETE`.
  3. **Sem retenção.** Nada expurga. O evento fica concluído e permanece.
- **O contraste é o que denuncia:** o mesmo texto, em `inbound_event`, é
  cifrado e apagado em 7 dias; em `message`, está sob RLS. No log do Modulith
  ele está em claro, sem isolamento e para sempre.
- **Não é vazamento hoje:** o acesso continua mediado pela aplicação, e nenhum
  endpoint expõe essa tabela. É exposição latente — e é exatamente o tipo de
  cópia esquecida que um pedido de exclusão de titular não alcançaria.
- **Correção aplicada**, em três frentes:
  1. `MensagemRecebidaEvent` deixou de declarar `String texto`. Ninguém o lia —
     o único consumidor já dizia, no próprio comentário, que não copiava texto
     para o motor de automações. Era peso morto serializado para sempre.
  2. `spring.modulith.events.completion-mode: delete`: publicação concluída
     deixa de existir em vez de virar linha permanente. O que a tabela serve —
     republicar o que **falhou** — continua intacto, porque falha não é
     conclusão.
  3. As 2334 publicações concluídas acumuladas, das quais 1119 carregavam
     texto de cliente, foram expurgadas com backup direcionado prévio. Nenhuma
     pendente foi tocada: havia zero.
- **Barreira contra regressão:** `PrivacidadeDeEventosTest` reprova qualquer
  campo de texto livre em evento de `api`, e não apenas o campo que falhou.
  Acrescentar `String` a um evento é o gesto natural de quem precisa de
  contexto; a barreira precisa estar no gesto. O caminho negativo foi
  exercitado: reintroduzi o campo e o teste acusou.

### `LGPD-002` — Categorias sem prazo algum — **MECANISMO PRONTO, PRAZOS PENDENTES**

- **Severidade:** média · **Mecanismo entregue em 2026-08-10**
- Contato, conversa, mensagem, auditoria, telemetria e diagnóstico de conector
  não têm prazo nem expurgo. Hoje isso é ausência de decisão, não decisão de
  reter para sempre — e a diferença precisa virar explícita.

### `LGPD-003` — Não há superfície de direitos do titular — **CORRIGIDO**

- **Severidade:** média · **Corrigido em 2026-08-10**
- Não existe endpoint de exportação, correção, anonimização ou exclusão. O
  catálogo de auditoria já reserva `EXPORT_REQUESTED` e `EXPORT_COMPLETED`,
  mas nada os emite: a intenção está registrada e a implementação não existe.

### `LGPD-004` — Backup sem tratamento documentado — **DOCUMENTADO**

- **Severidade:** média · **Documentado em 2026-08-10**
- O aceite exige que backup e réplicas tenham tratamento documentado. Hoje não
  há política, e o backup que **esta sessão criou** para a V22 é um exemplo
  concreto: um dump completo, com dado pessoal, num diretório temporário.
  Expurgo que não alcança backup não é expurgo.

---

## 6. O que preciso de você

Para a parte 2 — implementar o mecanismo — faltam decisões que não são minhas.
Não precisam vir todas de uma vez; a primeira já destrava bastante.

1. **Prazo por categoria.** Quanto tempo contato, conversa, mensagem,
   auditoria e telemetria devem ficar. Vale começar por uma faixa grosseira,
   desde que consciente.
2. **Papel por finalidade.** O protocolo alerta para não assumir que o tenant
   é sempre controlador: a plataforma provavelmente controla dado de conta,
   billing, segurança e fraude, e opera o dado de cliente final. Isso muda
   quem responde a um pedido de titular.
3. **Hipótese legal por atividade.**
4. **Legal hold.** Quem pode declarar, e sobre qual escopo.

As três últimas pedem validação competente. A primeira você pode decidir e
revisar depois — e é a que permite implementar e testar o mecanismo com
relógio controlado.

## 7. O que a parte 2 fará, independente das respostas

O mecanismo pode ser construído com os prazos como configuração, sem número
fixado no código:

- expurgo idempotente por categoria, provado com relógio controlado;
- legal hold que bloqueia expurgo dentro do escopo declarado;
- anonimização quando apagar for impossível — e recusa fundamentada, nunca
  falha silenciosa;
- exportação rastreável, minimizada ao pedido, auditada;
- verificação de identidade e autorização antes de exportar, corrigir ou
  excluir.

Nada disso é ligado por padrão. Um expurgo com prazo errado é irreversível de
um jeito que schema quebrado não é.

---

## 8. Situação em 2026-08-10

| Achado | Situação |
|---|---|
| `LGPD-001` | **Corrigido.** Texto fora do log do Modulith, passivo expurgado, teste de contrato vigiando |
| `LGPD-002` | **Mecanismo pronto**, prazos pendentes de decisão |
| `LGPD-003` | **Corrigido.** Exportação com procedência, anonimização e recusa fundamentada |
| `LGPD-004` | **Documentado** em `politica-de-backup-e-replica.md`; execução no Prompt 23 |

### O que o `LGPD-002` entregou, e o que falta

V23 traz legal hold sob RLS e seis funções de expurgo por categoria. Nenhuma
delas chama `now()`: o corte entra como parâmetro. Isso torna a retenção
verificável com relógio controlado e impede ligar o expurgo sem escolher a
data.

**Os prazos continuam `A VALIDAR` e o worker nasce desligado.** Zero desativa
cada categoria. Ligar exige preencher a tabela do item 1 com prazo, hipótese
legal e responsável — o mecanismo está pronto justamente para que a decisão
seja a única coisa que falte.

### O que o `LGPD-003` entregou

`POST /api/privacidade/titulares/{id}/exportacao` devolve o dado do titular
**dizendo de onde cada seção veio**: tabela de origem, finalidade e prazo — ou
a ausência dele, escrita com todas as letras. Uma lista sem origem obriga o
titular a confiar; com origem, ele confere.

`POST /api/privacidade/titulares/{id}/anonimizacao` remove o dado pessoal e
preserva a relação. Sob legal hold, **recusa com motivo e instrução**, nunca em
silêncio: devolver 200 sem apagar faria o titular sair acreditando que o dado
foi removido.

Ambos exigem `privacy.manage` com alcance de tenant — permissão separada de
`organization.manage`, porque responder a um direito não deveria exigir poder
de administrar papéis e canais. Ambos são auditados, com pedido e conclusão
registrados separadamente, e respondem `no-store`.

A exportação não inclui dado de terceiro: numa conversa, a identidade do
atendente aparece como função, porque ele é outro titular.
