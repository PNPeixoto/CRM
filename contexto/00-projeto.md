# CRM PNP — Descrição do Projeto

> Documento de contexto para o Claude Cowork. Deve ser lido no início de qualquer
> sessão de trabalho neste projeto. Mantido pelo desenvolvedor responsável.

---

## 1. O que é o produto

Plataforma SaaS de CRM omnichannel construída do zero, voltada inicialmente para
**redes de franquias**, com arquitetura preparada para atender outros segmentos.

O diferencial central é **centralizar o atendimento** de WhatsApp, Instagram,
Telegram e chat ao vivo em uma única caixa de entrada, permitindo múltiplos
números, contas, canais e unidades **dentro da mesma licença** — sem cobrar uma
licença por número.

O produto não é uma cópia de Kommo, HubSpot, Salesforce, Pipedrive ou Monday.
Essas ferramentas servem apenas como referência de boas práticas.

**Cenário-alvo de referência:** uma franqueadora fictícia com 42 unidades,
operando locação de brinquedos. O módulo de reservas e ativos deve ser
desacoplável, porque o segmento inicial não é o segmento final.

### Ciclo de vida de uma mensagem

Este é o fluxo que define o núcleo do domínio:

1. Identificar canal e conta de origem
2. Localizar ou criar o contato
3. Verificar duplicidade
4. Criar ou associar conversa
5. Identificar unidade/franquia responsável
6. Distribuir para fila ou atendente
7. Registrar histórico
8. Criar ou atualizar oportunidade
9. Disparar automações
10. Acompanhar até a conclusão

---

## 2. Stack

**Backend**
- Java 25 LTS
- Spring Boot 4.1
- Spring Modulith (monólito modular — **não** microsserviços)
- PostgreSQL + Flyway
- Redis/Valkey (cache, filas, presença, rate limit)
- WebSocket + STOMP (tempo real)
- OpenAPI como contrato, com client TypeScript gerado

**Frontend**
- React + TypeScript
- Vite
- Tailwind CSS

**Infraestrutura**
- Início: Hostinger
- Destino: AWS

---

## 3. Princípios inegociáveis

Estes princípios têm precedência sobre conveniência, estética e velocidade de
entrega. Qualquer sugestão que os viole deve ser recusada e substituída.

1. **Nunca confiar no frontend.** Toda validação, autorização e cálculo é
   refeito no backend. O frontend é conveniência de UX, nunca fronteira de
   segurança.
2. **Segurança antes de estética.** Se houver conflito, a segurança vence e a
   interface se adapta.
3. **Segredos nunca aparecem por valor.** Apenas o *nome* da variável de
   ambiente (`ERP_API_KEY`, `META_ACCESS_TOKEN`, `WEBHOOK_SECRET`). Credencial
   de teste deve ser explicitamente marcada como "trocar antes de produção".
4. **`tenant_id` desde o primeiro commit.** Multiempresa não se adiciona depois.
5. **`tenant_id` nunca vem do cliente.** É resolvido no backend a partir do
   token/host e injetado no contexto da requisição.
6. **Banco é a fonte da verdade.** Cache é otimização descartável — se o Redis
   sumir, o sistema recalcula do Postgres.
7. **Código limpo, sem over-engineering.** Abstração só quando existe o segundo
   caso de uso concreto.
8. **Toda decisão técnica vem explicada.** O objetivo do projeto é também
   aprendizado: explicar o porquê da escolha, as alternativas descartadas e o
   trade-off aceito.

---

## 4. Multi-tenancy e isolamento

- Modelo: tabelas compartilhadas com `tenant_id` + **Row Level Security** no
  PostgreSQL.
- RLS é a segunda linha de defesa: se uma query esquecer o filtro, o banco
  bloqueia. Não confiar apenas no código da aplicação para evitar vazamento
  entre clientes.
- Hierarquia de escopo: **Franqueadora → Regiões → Unidades → Equipes →
  Usuários**. Toda consulta carrega o escopo do usuário autenticado.
- Perfis previstos: superadministrador da plataforma, administrador da
  franqueadora, gestor regional, gestor de unidade, atendente/vendedor,
  financeiro e auditor (somente leitura).
- **A interface esconde o que não é autorizado**, não apenas desabilita. E o
  backend rejeita de qualquer forma — esconder na UI é cosmético.

---

## 5. Módulos do domínio

Cada módulo é um pacote Spring Modulith com fronteira explícita. Comunicação
entre módulos por eventos de aplicação, não por chamada direta a repositório de
outro módulo.

| Módulo | Responsabilidade |
|---|---|
| `identity` | Usuários, autenticação, MFA, sessões |
| `tenant` | Empresas, unidades, regiões, equipes, territórios |
| `authz` | Perfis, permissões por módulo/ação/registro/campo |
| `contact` | Contatos, empresas, duplicidade, campos personalizados |
| `conversation` | Conversas, mensagens, filas, atribuição, SLA |
| `channel` | Conexões de WhatsApp, Instagram, Telegram, chat ao vivo |
| `routing` | Regras de distribuição de leads e conversas |
| `deal` | Oportunidades, funis, etapas, motivos de ganho/perda |
| `task` | Tarefas, agenda, lembretes, recorrência |
| `automation` | Gatilhos, condições, ações, execuções e logs |
| `integration` | Conector HTTP genérico, agentes privados, credenciais |
| `booking` | Reservas, produtos e ativos (**desativável por segmento**) |
| `campaign` | Segmentação e disparos |
| `report` | Relatórios, filtros globais, exportação |
| `audit` | Trilha append-only com retenção por categoria |
| `billing` | Plano, licença, consumo, cobrança |

---

## 6. Decisões de arquitetura já tomadas

- **Auditoria é append-only durante sua retenção.** Registro não é editado;
  correção entra como novo evento. Cada categoria possui finalidade, fundamento,
  acesso, prazo e descarte/anonimização verificável. Append-only não significa
  retenção eterna.
- **Segredos de integração podem viver no ambiente do cliente**, via agente
  privado. O CRM central armazena apenas a *referência* ao segredo, nunca o
  valor. Isso precisa estar refletido no modelo de dados e na UI.
- **Conector HTTP genérico é no-code**, com variáveis interpoladas
  (`{{contact.name}}`, `{{secret.ERP_API_KEY}}`). O valor do segredo nunca é
  exibido nem retornado pela API, nem em log, nem em resposta de teste.
- **Chat ao vivo é o primeiro canal a ser construído**, porque não depende de
  aprovação de API de terceiro e já valida toda a espinha dorsal: conversa,
  fila, atribuição, tempo real, histórico.
- **Erros de integração são sanitizados antes de virar log visível ao usuário** —
  resposta de API externa pode conter credencial.
- **Timestamps em UTC no banco**, exibidos no timezone configurado pelo tenant
  ou unidade; `America/Sao_Paulo` é apenas o default brasileiro inicial.
- **Valores monetários em centavos, como inteiro.** Nunca `float`/`double`.

---

## 7. Direção visual

- Fonte: **Manrope** (interface) e **JetBrains Mono** (dados técnicos, IDs, logs)
- Cor principal: índigo `#4B2ED4` (7,95:1 sobre branco — WCAG AAA)
- SaaS premium, limpo, alta densidade de informação sem poluição
- Bordas suaves, sombras discretas, espaçamento consistente, hierarquia forte
- Status nunca depende só de cor — sempre acompanhado de ícone ou texto
- Sem aparência infantil, mesmo com cliente do ramo de brinquedos
- Breakpoints: 1440px (principal), 1280px, tablet, mobile
- Acessibilidade WCAG 2.2 AA: contraste, teclado, foco não oculto, labels, alvo
  mínimo, redução de movimento, zoom e reflow

### Tema — resolvido em 2026-07-27

**Modo claro é o padrão.** Não havia conflito com o briefing: a leitura de que
o protótipo era escuro estava errada. `#15121F` aparece nele apenas como
sidebar, painel do login, moldura do mockup mobile e preview de white label —
o canvas sempre foi `#F6F6FA` com texto `#17171F`. O padrão é **claro com
shell de navegação escuro**.

Tokens nomeados por papel, nunca por valor:

| Usar | Não usar |
|---|---|
| `--surface-base`, `--surface-raised` | `--gray-50`, `--gray-100` |
| `--text-strong`, `--text-muted` | `--gray-900` |
| `--border-subtle` | `--gray-200` |

Regra derivada: **nenhum hex literal em componente**. Cor que não existe como
token vira token antes de ser usada. Toda cor semântica (sucesso, erro, alerta,
informação) nasce com par claro/escuro definido — senão o modo escuro depois
vira reescrita da paleta inteira.

O seletor de tema é entregue **depois do P0**. A estrutura existe desde já; a
funcionalidade não é P0.

---

## 8. Estado atual

- Monólito modular Spring Boot e frontend React já existem e são executáveis.
- Autenticação/sessão, tenants, contatos, oportunidades, tarefas, conversa,
  canais, fila de saída e apresentação por segmento possuem fundações reais.
- PostgreSQL usa migrations Flyway, RLS e papéis separados para migration e
  runtime; a suíte de integração usa PostgreSQL real.
- O protótipo visual continua sendo referência, não código-fonte do produto.
- O retrato operacional detalhado e o próximo passo ficam exclusivamente em
  `contexto/02-estado-atual.md`.

---

## 9. Prioridades

**P0 — MVP**
Login, usuários, empresas, unidades, permissões, contatos, oportunidades, funil,
tarefas, caixa omnichannel, chat ao vivo, múltiplos números, distribuição de
conversas, histórico, respostas rápidas, dashboard básico, auditoria,
configurações.

**P1**
WhatsApp, Instagram, Telegram, automações, integrações, agente local, reservas,
produtos, estoque, relatórios avançados, campanhas.

**P2**
IA (previsão de conversão, resumos, sugestão de resposta, análise de
sentimento), construtor avançado de relatórios, marketplace de integrações,
white label completo.

Nada de P1 ou P2 deve ser iniciado enquanto houver item de P0 aberto.

---

## 10. Convenções

- Migrations sempre via Flyway, nunca `ddl-auto: update`
- Nomes de tabela e coluna em `snake_case`, em inglês
- Textos de interface em **português do Brasil**, dados fictícios brasileiros
  (nomes, cidades, telefones, valores em reais)
- Entidade de negócio carrega tenant e auditoria de criação/alteração; evento
  append-only, fila técnica e referência global seguem campos próprios da sua
  categoria, conforme `01-padroes-tecnicos.md`
- Operação de alto impacto ou relevante para rastreabilidade gera auditoria;
  não se grava payload ou dado sensível por padrão
- Operações que podem ser reenviadas (webhook, disparo, criação) precisam de
  chave de idempotência com `UNIQUE` no banco
- DTOs de entrada e saída separados das entidades JPA
- Testes obrigatórios em: regra de autorização, isolamento entre tenants e
  cálculo de valores

---

## 11. Como trabalhar neste projeto

- Explicar as escolhas técnicas e como as chamadas foram feitas — o objetivo é
  aprendizado, não apenas entrega
- Explicações diretas e informativas, sem enrolação
- Apontar quando uma decisão minha estiver errada ou criar dívida técnica, com o
  motivo concreto
- Antes de criar abstração nova, verificar se já existe algo equivalente no
  projeto
- Não gerar código para P1/P2 quando o P0 correspondente não existe
- Nunca inventar credencial, endpoint ou nome de serviço externo sem confirmar
