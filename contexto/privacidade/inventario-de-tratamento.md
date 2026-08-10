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
| **Log de eventos do Modulith** | `event_publication.serialized_event` | cliente do cliente | **nenhuma** — ver achado `LGPD-001` |

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

A exceção é a tabela do item 1 marcada como achado.

## 5. Achados do levantamento

### `LGPD-001` — Texto de mensagem fora do isolamento e de toda retenção

- **Severidade:** alta
- **Onde:** `event_publication`, tabela interna do Spring Modulith.
- **O fato:** `MensagemRecebidaEvent` declara `String texto` e é serializado
  nessa tabela. Em 2026-08-10 havia **871 eventos** desse tipo, publicados
  entre 09 e 10 de agosto.
- **Por que importa, em três camadas:**
  1. **Sem RLS.** `relrowsecurity` e `relforcerowsecurity` são `f`. É a única
     tabela do schema sem isolamento por tenant — todas as outras 38 têm.
  2. **Acesso total do runtime:** `INSERT, SELECT, UPDATE, DELETE`.
  3. **Sem retenção.** Nada expurga. O evento fica concluído e permanece.
- **O contraste é o que denuncia:** o mesmo texto, em `inbound_event`, é
  cifrado e apagado em 7 dias; em `message`, está sob RLS. No log do Modulith
  ele está em claro, sem isolamento e para sempre.
- **Não é vazamento hoje:** o acesso continua mediado pela aplicação, e nenhum
  endpoint expõe essa tabela. É exposição latente — e é exatamente o tipo de
  cópia esquecida que um pedido de exclusão de titular não alcançaria.
- **Correção candidata:** parar de trafegar texto no evento, passando só
  identificadores, como o push de WebSocket já faz; ou aplicar RLS e retenção
  à tabela. A primeira é preferível: o texto não precisa estar ali.

### `LGPD-002` — Categorias sem prazo algum

- **Severidade:** média
- Contato, conversa, mensagem, auditoria, telemetria e diagnóstico de conector
  não têm prazo nem expurgo. Hoje isso é ausência de decisão, não decisão de
  reter para sempre — e a diferença precisa virar explícita.

### `LGPD-003` — Não há superfície de direitos do titular

- **Severidade:** média
- Não existe endpoint de exportação, correção, anonimização ou exclusão. O
  catálogo de auditoria já reserva `EXPORT_REQUESTED` e `EXPORT_COMPLETED`,
  mas nada os emite: a intenção está registrada e a implementação não existe.

### `LGPD-004` — Backup sem tratamento documentado

- **Severidade:** média
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
