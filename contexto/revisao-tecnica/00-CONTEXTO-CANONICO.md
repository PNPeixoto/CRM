# Contexto canônico para revisão — CRM PNP

> Fotografia de referência criada em 2026-08-02. O revisor deve confirmar esta
> fotografia no código antes de usá-la como prova.

## 1. Produto e objetivo

O CRM PNP é um SaaS omnichannel, inicialmente orientado a redes, franquias e
operações com múltiplas unidades. Ele centraliza atendimento, contatos,
oportunidades e tarefas, começando por WhatsApp, Instagram, Telegram e chat ao
vivo. Uma licença pode reunir múltiplas contas, unidades, equipes e usuários.

O fluxo essencial de uma mensagem é:

1. identificar e autenticar o canal;
2. localizar ou criar o contato;
3. deduplicar o evento recebido;
4. localizar ou criar a conversa;
5. determinar tenant, unidade e contexto organizacional;
6. rotear para equipe ou responsável;
7. persistir o histórico de forma durável;
8. relacionar oportunidade, tarefa ou automação quando aplicável;
9. concluir ou reabrir o atendimento de forma rastreável.

## 2. Princípios que não podem ser negociados

- O frontend nunca é fronteira de segurança.
- O `tenant_id` vem da identidade ou do host verificado, nunca de body, query ou
  header fornecido livremente pelo cliente.
- Autorização considera ação, escopo e registro; esconder um item do menu é
  apenas apresentação.
- PostgreSQL é a fonte de verdade. Redis e WebSocket aceleram experiências, mas
  não substituem persistência durável.
- Segurança e isolamento entre tenants têm precedência sobre estética e
  conveniência.
- Segredos aparecem apenas por nome de variável. Valores nunca entram em código,
  log, documento, teste ou resposta da API.
- Migrations Flyway já aplicadas são imutáveis. Mudança de schema recebe uma nova
  migration.
- Timestamps são UTC/`TIMESTAMPTZ`; valores monetários são inteiros em centavos.
- Auditoria é append-only durante a retenção definida, não retenção eterna.
- Não criar camadas, domínios ou abstrações sem uma regra ou pressão real.
- Toda decisão relevante deve ser explicada e verificável.

## 3. Arquitetura pretendida

### Backend

- Java 25, Spring Boot 4.1 e Spring Modulith.
- Monólito modular com módulos de negócio isolados por API pública.
- Fluxo usual: controller → aplicação → domínio quando há invariantes →
  persistência.
- DTOs de entrada e saída separados de entidades.
- Eventos para efeitos assíncronos; chamada direta para dependência síncrona
  explícita.
- PostgreSQL com Flyway e RLS; Redis para usos temporários apropriados.
- OpenAPI 3.1 como contrato HTTP e WebSocket STOMP para atualizações incrementais.

Módulos previstos: `identity`, `tenant`, `authz`, `contact`, `conversation`,
`channel`, `routing`, `deal`, `task`, `automation`, `integration`, `booking`,
`campaign`, `report`, `audit` e `billing`. A existência no mapa não significa que
todos já estejam implementados.

### Frontend

- React, TypeScript, Vite e Tailwind.
- Contrato OpenAPI gerado isolado em adaptadores; páginas consomem modelos da
  aplicação e não importam diretamente o código gerado.
- Cliente HTTP central para autenticação, CSRF, timeout, cancelamento,
  correlação, paginação e retry seguro.
- REST é fonte de verdade; eventos em tempo real atualizam a interface de forma
  incremental e reconciliável.

## 4. Modelo organizacional e autorização

- `tenant` é a raiz de isolamento da empresa/rede.
- `unit` representa uma unidade operacional.
- `app_user` é uma identidade interna; `contact` é uma pessoa ou organização
  atendida e não deve ser confundido com usuário.
- O usuário possui membership temporal no tenant, papéis, permissões e escopos.
- Alcances conceituais: `NETWORK > TENANT > UNIT > TEAM > OWN`; o estado atual
  persiste principalmente tenant, unidade e próprio.
- Papel agrupa permissões; escopo limita onde elas valem.
- Capability técnica, entitlement comercial, permissão, escopo e visibilidade de
  navegação são conceitos independentes.
- A API pública `OrganizationAccess` é a fronteira organizacional esperada.

## 5. Regras de negócio implementadas que exigem regressão

### Contatos

- Contato aceita pessoa ou organização; pessoa é o padrão atual.
- Campos opcionais em branco são normalizados para ausência.
- Responsável precisa estar ativo e pertencer ao mesmo tenant.
- Exclusão é lógica.

### Funis e oportunidades

- Status aberto, ganho ou perdido é coerente com a etapa do funil.
- Mover, ganhar, perder e reabrir preservam datas e motivo de perda de forma
  consistente.
- Valor é armazenado em centavos.
- Contato e responsável pertencem ao mesmo tenant.
- O funil padrão é criado uma única vez após onboarding, com proteção contra
  concorrência e etapas de acordo com o segmento.

### Tarefas

- Responsável, contato e oportunidade pertencem ao mesmo tenant.
- Concluir/reabrir controla a data de conclusão no servidor.
- Exclusão é lógica.

### Conversas e canais

- REST fornece o estado canônico e WebSocket fornece incrementos.
- Envio cria mensagem pendente e chama o provedor fora da requisição.
- Entrada resolve o tenant pelo canal autenticado, deduplica e persiste antes de
  responder sucesso ao provedor.
- Credenciais de canal são write-only; respostas expõem no máximo indicadores.
- Erros de provedor apresentados ao usuário são sanitizados.

### Onboarding e apresentação

- Presets atuais: serviços gerais, confeitaria, restaurante e locação.
- O preset altera rótulos, ordem/visibilidade de navegação e funil padrão, mas
  não concede autorização.
- Repetir o onboarding com o mesmo segmento é idempotente; tentar trocar o
  segmento depois da conclusão causa conflito.

### Relatórios

- Relatórios usam APIs públicas dos módulos, sem consultar tabelas privadas de
  outros módulos.
- Conversão considera ganhos sobre ganhos mais perdidos, exclui abertos e
  arredonda em uma casa decimal.

## 6. Estado conhecido na data de referência

- Prompts backend 00–05 e frontend F0–F3 constam como concluídos.
- Gate A consta como fechado; Gate B continua aberto.
- Banco está em V11, com modelo organizacional, sessões, recuperação e MFA.
- Backend tinha 82 testes verdes; frontend, 56 testes em 14 arquivos. Esses
  números são histórico, não evidência da execução atual.
- OpenAPI determinístico e tipos TypeScript gerados estão versionados.
- Rotas funcionais de frontend: dashboard, inbox, contatos, funis, tarefas,
  integrações e relatórios.
- Calendário, reservas, ativos, unidades, equipes, automações, campanhas,
  auditoria e configurações ainda podem ser placeholders.
- O broker STOMP em memória impede escala horizontal sem evolução arquitetural.
- Persistem riscos declarados: blocklist inicial de senha, entrega externa de
  reset, TOTP suscetível a phishing, janela residual de access token de 15
  minutos, duas vulnerabilidades altas no `npm audit` e autorização fina ainda
  pertencente às etapas seguintes.

## 7. Gates

- **A — Fundação:** ambiente, builds, PostgreSQL real, papéis DB, RLS e modelo
  organizacional.
- **B — Segurança do núcleo:** autenticação, autorização por ação/escopo/registro,
  IDOR, ASVS, threat models e supply chain.
- **C — Produto utilizável:** contratos, acessibilidade, slices P0 e E2E.
- **D — Omnichannel:** webhooks, idempotência, adapters, mídia e reconciliação.
- **E — Operação e conformidade:** auditoria, retenção, billing, SLO, backup,
  rollback e entrega rastreável.
- **F — Integrações privadas e escala:** agente privado, modularidade e carga.

Um status escrito em manifesto não substitui a reexecução das evidências.

## 8. Ordem de precedência para a revisão

1. Princípios de produto em `contexto/00-projeto.md`.
2. ADRs aceitos em `contexto/decisoes/`.
3. Código, migrations, configuração e testes executáveis atuais.
4. Padrões em `contexto/01-padroes-tecnicos.md`.
5. Fotografia em `contexto/02-estado-atual.md`.
6. Manifests e prompts, somente para distinguir realizado de planejado.

Quando duas fontes conflitarem, o revisor deve registrar o conflito, avaliar o
risco e propor qual fonte precisa ser corrigida. “O código vence” descreve o
comportamento atual, mas não transforma comportamento incorreto em requisito.

## 9. Fontes obrigatórias

Leia integralmente antes de concluir:

- `CLAUDE.md`, se existir;
- `contexto/00-projeto.md`;
- `contexto/01-padroes-tecnicos.md`;
- `contexto/02-estado-atual.md`;
- `contexto/glossario.md`;
- `contexto/modelo-organizacional.md`;
- `contexto/decisoes/*.md`;
- `contexto/prompts/PREAMBULO.md`;
- `contexto/prompts/GATES.md`;
- manifests backend e frontend;
- `backend/AUTENTICACAO.md`, `backend/BANCO.md` e `backend/TESTES.md`;
- `frontend/CONTRATO-HTTP.md` e `frontend/README.md`;
- configurações de build, CI, containers e ambiente;
- código, migrations e testes pertinentes ao escopo revisado.

