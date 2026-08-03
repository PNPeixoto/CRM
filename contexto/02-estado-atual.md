# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: 2026-08-03 12:15 (America/Montevideo)

## Onde parei

Os Prompts backend 00–07 e frontend F0–F3 estão concluídos. O Gate A está
fechado. O Gate B está **aprovado do lado backend** e **aberto no conjunto**:
faltam as provas de frontend F4A e F4. Autorização, sessão e supply chain de
backend estão cobertas; segurança de navegador é a metade que falta.

O backend está na migration V11 e a suíte tem 112 testes verdes. Próximo
executável: `frontend:F4A`; F4 depende dele. `backend:08` fica para depois da
revisão do Gate B, e já tem quatro itens endereçados a ele no backlog de
segurança.

## Implementado

- Imagem multi-stage Temurin 25, runtime UID/GID 10001, filesystem somente
  leitura, health checks e profiles dev/test/prod separados.
- PostgreSQL 17 e Redis 7 em desenvolvimento; produção exige configuração e
  segredos externos e falha fechada.
- Política de testes com gates rápidos, integração real, carga opt-in,
  quarentena governada e fixtures isoladas.
- Papéis `crm_migrator`/`crm_runtime`, RLS `ENABLE + FORCE`, privilégios mínimos,
  tenant derivado apenas de credencial e integridade composta entre tenants.
- Modelo organizacional com unidade, membership temporal, papéis, permissões e
  escopos; contato B2B/B2C; presets e onboarding por segmento.
- Argon2id com pepper externo, login uniforme e rate limit por
  tenant/login/origem mais limite global de origem sem lock global da vítima.
- Access token de 15 minutos; refresh por hash, rotação/família/reuso, uma hora
  de inatividade, 24 horas absolutas, logout e revogação de todos os dispositivos.
- Reset de senha uniforme, token único de 256 bits/15 minutos armazenado por
  hash, política atual e revogação de todas as sessões.
- MFA TOTP obrigatório para OWNER/ADMIN/SUPERADMIN, segredo AES-256-GCM, replay
  bloqueado e dez códigos de recuperação de uso único armazenados por hash.
- **Autorização por ação e por registro em ponto único (`Autorizacao`), aplicada
  a todas as superfícies HTTP atuais.** Alcance vem do membership vigente; o recorte por
  responsável entra na consulta, não sobre a página. Atualização verifica antes
  e depois de aplicar, para impedir tanto editar registro alheio quanto
  transferir o próprio para fora do alcance. Usuário sem membership vigente
  perde acesso na requisição seguinte, sem esperar o token expirar.
- Recursos sem responsável e compartilhados pelo tenant — canais, caixa de
  entrada, relatórios e mutação do onboarding — exigem explicitamente alcance
  `TENANT`; `OWN` nunca é promovido a acesso coletivo.
- Referências entre contato, oportunidade e tarefa são autorizadas no registro
  relacionado, e a permissão da ação é verificada antes de buscar o id alvo.
- **Inscrição em tópico de tempo real revalida permissão e alcance `TENANT`**,
  além de aceitar somente destinos STOMP conhecidos, pela porta
  `AutorizacaoDeEscuta`.
- `GET /api/organizacao/permissoes` devolve permissão → alcance, para o menu
  esconder o que não se usa sem que esconder seja a proteção.
- OpenAPI 3.1 determinístico via springdoc 3.0.3 e snapshot versionado.
- Erro RFC 9457 com status, código, campos, instância e correlation id; detalhes
  internos não são apresentados pelo frontend.
- `openapi-typescript` 7.13.0 fixo e saída gerada versionada. Apenas o adaptador
  importa o contrato; páginas usam modelos próprios.
- Cliente HTTP central com cookies, token em memória, CSRF, timeout,
  `AbortSignal`, correlação, paginação e retry limitado. Escrita só repete com
  chave e contrato de idempotência explícitos.
- CI verifica a suíte backend, snapshot OpenAPI, geração TypeScript sem diff,
  lint, testes e build frontend.
- Job de segurança no CI: gitleaks sobre o histórico completo, auditoria de
  dependência com exceção nomeada e revisão de dependência em PR. A exceção
  exige responsável, prazo e fundamento, e o gate **reprova quando o prazo
  vence** — aceite sem prazo vira permissão permanente.
- Dependabot acompanha maven, npm, github-actions e docker.
- Baseline ASVS 5.0.0 nível 2, threat models por fluxo e backlog priorizado em
  `contexto/seguranca/`.

## Migrations atuais

- V1–V5: base do CRM, conversas, canais, fila e operações principais.
- V6: registro de eventos do Spring Modulith.
- V7: perfil e apresentação do tenant.
- V8: referências compostas multi-tenant.
- V9: privilégios mínimos de funções.
- V10: modelo organizacional e escopos.
- V11: sessão endurecida, recuperação e MFA.
- Seeds e dados demonstrativos existem somente no profile `dev`. O seed separa
  `ATTENDANT` com alcance `OWN` nos registros e `ATTENDANT_SHARED` com alcance
  `TENANT` apenas para a caixa de entrada compartilhada.

## Verificado nesta máquina

- Baseline `a603534` + árvore desta sessão, branch `main`, Windows/JDK 25.0.4 e Docker Desktop 29.6.2.
- Backend: 112 testes, 0 falhas, 0 erros, 0 ignorados; PostgreSQL/Redis reais com
  runtime restrito `crm_runtime_test`.
- Flyway limpo até V11, caminho de atualização e os 14 artefatos do conjunto de
  migrations + seeds `dev` verificados.
- Benchmark Argon2id local: média de 103 ms, cinco amostras após aquecimento.
- Frontend: 56 testes em 14 arquivos, todos verdes, reexecutados em 2026-08-03
  antes de versionar.
- `npm run api:check` e build de produção passaram em 2026-08-01.
- Lint frontend sem erros; permanecem três avisos conhecidos de Fast Refresh.

## Governança

- `contexto/prompts/manifest.yaml` v3 é a trilha backend canônica.
- `contexto/prompts/frontend/manifest.yaml` v4 é a trilha frontend companheira.
- Prompts 00–07 e F0–F3 estão marcados como `completed`; a revisão corretiva do
  Prompt 06 está registrada na sessão de 2026-08-03.
- ADRs individuais registram banco, modelo organizacional, autenticação e o
  alcance de autorização (ADR-0008).
- A revisão integrada de 2026-08-02 permanece em
  `contexto/revisao-tecnica/resultados/`, com notas de fechamento por achado.
- O acervo acumulado foi versionado em 2026-08-03 na `main`, em cinco commits
  por área (infra/CI, backend, frontend, documentação, contexto), sobre
  `793777d`. Ambas as suítes estavam verdes no momento do commit. Nenhum
  segredo foi versionado: o único arquivo de ambiente rastreado é
  `.env.example`, que contém apenas nomes.

## Próximo passo

1. Executar `frontend:F4A` para segurança do navegador e supply chain; depois
   `frontend:F4` para sessão/refresh na UX.
2. Revisão integrada do Gate B, consolidando backend 05–07 e frontend F4A/F4.
3. Executar `backend:08`, que já tem quatro itens endereçados no backlog de
   segurança: `SEC-001`, `SEC-003`, `SEC-006` e `SEC-012`.

## Riscos restantes

- Alcance por unidade não decide sobre registro de domínio: nenhuma tabela de
  domínio declara unidade. Falha fechada por decisão registrada em ADR-0008; a
  saída é migration aditiva com regra de backfill.
- O seletor de contexto do frontend segue alimentado por lista fabricada no
  cliente; `/api/organizacao/contextos` e `/api/organizacao/permissoes` ainda não
  têm consumidor.
- Auditoria — classificada P0 pelo próprio produto — não existe (AUDIT-001);
  bloqueia o Gate E.
- Cinco riscos médios abertos em `contexto/seguranca/backlog.md`: contrato
  OpenAPI público, ausência de limite de taxa e de tamanho de corpo fora do
  login, deriva de schema não detectada, inscrição WebSocket que sobrevive à
  revogação, e falha de infraestrutura que se apresenta como falha de teste.
- A blocklist de senha é inicial; precisa de fonte mantida antes da produção pública.
- Entrega de reset exige URL HTTPS/provedor configurado externamente em produção.
- TOTP não é resistente a phishing; passkeys/WebAuthn seguem como evolução.
- Revogar refresh não invalida access token já emitido; janela máxima é 15 minutos.
- As duas vulnerabilidades altas do `npm audit` são de RSC no `react-router` e
  não alcançam este frontend, que usa só `BrowserRouter`. Exceção nomeada com
  prazo em 2026-11-30; o gate reprova quando vencer.
- O broker STOMP em memória ainda impede escala horizontal.
- Exportação e job não existem como superfície; a revalidação que o Prompt 06
  exige para eles é herdada por `Autorizacao` quando forem criados, e o Prompt 21
  é onde se comprova.
