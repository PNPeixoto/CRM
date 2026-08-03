# Preâmbulo canônico — Trilha frontend v4

## Fonte e escopo

Esta trilha complementa `contexto/prompts/manifest.yaml` e substitui a proposta
“CRM PNP — Trilha de Frontend (v3)”. Aplique também o preâmbulo canônico do
pacote principal em `../PREAMBULO.md`. Em divergência, código e configuração
executáveis vencem; registre a diferença antes de alterá-la.

O prefixo canônico desta trilha é `frontend:`. Dependências do pacote principal
usam `backend:<id>`; dependências locais usam `frontend:<id>`. A trilha é
paralela, mas uma capacidade não avança quando sua dependência cruzada não
possui evidência.

## Momento de bloqueio

- `before-demo`: necessário antes de demonstrar a jornada correspondente;
- `before-external-pilot`: necessário antes de usar dados externos/reais;
- `before-production`: necessário antes de produção com cliente;
- `feature-activation:<capacidade>`: necessário para ativar a capacidade;
- `evidence-triggered`: execute somente após medição demonstrar necessidade.

O momento de bloqueio organiza risco; não reduz critério de aceite nem autoriza
dados reais antes dos controles aplicáveis.

## Protocolo de execução

1. Leia `CLAUDE.md`, contexto consolidado, este preâmbulo, o prompt e suas
   dependências.
2. Preserve stack, tokens, fontes e componentes existentes. Dependência nova
   exige lacuna comprovada e justificativa; ADR quando fechar uma escolha cara.
3. Não crie abstração, componente, tema, tabela avançada, flag ou cache offline
   sem consumidor real.
4. Cada PR entrega seus testes no nível mais barato que prove o risco. F12 é
   auditoria de lacunas, não o início dos testes.
5. Frontend nunca concede autorização, escolhe tenant/unidade por dado não
   autorizado, calcula regra financeira ou relaxa limite do servidor.
6. Registre evidência com commit, ambiente, data, comando, resultado, artefato
   e responsável, sem segredo, token, payload, mensagem ou dado pessoal.

## Contratos transversais

- Transporte deriva de OpenAPI determinístico. Código gerado não é editado e
  componentes não o importam diretamente: `gerado → adaptador → apresentação`.
- DTO manual duplicando transporte é proibido; view model, estado de UI, tipo de
  formulário e modelo de apresentação são permitidos.
- Erro HTTP parte de RFC 9457 Problem Details, com código estável, erros de
  campo, ocorrência e correlação. `detail` só aparece se o contrato o marcar
  explicitamente como seguro; o cliente localiza mensagens por código.
- Status 400/422 seguem a semântica publicada pelo backend; não se fixa um
  deles por preferência. 403 serve módulo conhecido sem permissão; 404 pode
  ocultar registro fora do escopo para evitar enumeração.
- Instante usa timestamp com offset/UTC; data civil usa `YYYY-MM-DD`; horário
  local carrega timezone da unidade. Não converta aniversário ou reserva para
  UTC indiscriminadamente.
- Dinheiro usa `amountMinor` e `currency` ISO 4217. Não presuma duas casas nem
  que `int64` cabe em `number`; cálculo e arredondamento pertencem ao backend.

## Segurança do navegador

- Access token somente em memória; refresh em cookie HttpOnly/Secure. Token,
  mensagem, contato ou PII nunca vai para localStorage, sessionStorage ou
  IndexedDB.
- SameSite é defesa adicional, não substitui proteção CSRF. Endpoints baseados
  em cookie validam origem e usam token anti-CSRF quando a topologia exigir.
- Conteúdo externo é texto não confiável. Proíba execução dinâmica, URLs e
  esquemas perigosos e HTML cru sem sanitização central aprovada.
- CSP, dependências, scripts de instalação, source maps e telemetria seguem
  allowlist e minimização. Cache offline/PWA exige decisão explícita de risco.

## Aceite transversal de todo PR frontend

1. Funciona com teclado e foco visível?
2. Possui loading, empty, error e success quando aplicáveis?
3. Há teste no nível mais barato que prova o comportamento?
4. API é acessada somente pela camada oficial?
5. Não há token/PII em armazenamento, log ou telemetria?
6. Troca de tenant/unidade invalida dados relevantes?
7. Falha não exibe stack trace ou detalhe interno?
8. Rede lenta e falha parcial mantêm a interface compreensível?
9. Bundle da rota não regrediu sem justificativa?
10. Mobile foi testado ou explicitamente limitado?
11. Alguma abstração foi criada sem segundo uso concreto?
12. Texto, data ou dinheiro impedem evolução futura?

Quarentena de teste segue o pacote principal e é vedada para sessão,
isolamento, contrato de segurança e proteção de dados.
