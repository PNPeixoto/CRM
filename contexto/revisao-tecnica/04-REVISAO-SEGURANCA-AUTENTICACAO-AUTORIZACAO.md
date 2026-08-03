# Prompt — segurança, autenticação e autorização

## Início do prompt

Faça uma revisão de segurança defensiva e somente leitura do CRM PNP, com foco
em isolamento multi-tenant, autenticação, sessão e autorização. Não use nem
exponha credenciais reais, não envie mensagens externas e não faça teste
destrutivo. Leia `00-CONTEXTO-CANONICO.md`, `backend/AUTENTICACAO.md`, ADRs,
matriz ASVS existente e `contexto/prompts/GATES.md`. Use o modelo de achado.

O simples fato de existirem tabelas de papel/escopo ou itens ocultos no frontend
não comprova autorização. Procure a decisão no backend e a restrição no banco.

### Modelo de ameaça mínimo

Considere: usuário anônimo, usuário autenticado comum, operador com escopo OWN,
gestor de unidade, administrador do tenant, tenant malicioso, provedor externo,
browser comprometido por XSS e reutilização de token roubado. Para cada jornada
crítica, identifique ativo, fronteira de confiança, ameaça, controle e evidência.

### Autenticação local

- Enumeração uniforme de empresa/login e diferenças de status, corpo e tempo.
- Argon2id, pepper externo, parâmetros, rehash e política/blocklist de senha.
- Rate limit por origem e identidade sem permitir lockout global provocado.
- Reset: token aleatório, hash em repouso, uso único, 15 minutos, resposta
  uniforme, URL/provedor seguro e revogação das sessões.
- MFA: obrigatoriedade administrativa, segredo protegido, ativação segura, replay
  TOTP, janela de tempo, códigos de recuperação únicos e fluxo de recuperação.
- Não registrar senha, código, token, segredo ou motivo interno sensível.

### Sessões e tokens

- Access token de 15 minutos; refresh com família, rotação, detecção de reuso,
  inatividade de 1 hora e duração absoluta de 24 horas.
- Logout atual, revogação de todos os dispositivos e comportamento concorrente.
- Validação de emissor, audiência, algoritmo, expiração, clock skew, tipo e claims.
- Cookies, CSRF, CORS, CSP, headers, armazenamento em memória e persistência no
  browser.
- Refresh simultâneo/single-flight e janela residual após revogação.
- Erros externos uniformes, sanitizados e compatíveis com o frontend.

### Autorização e multi-tenancy

Construa uma matriz `ação × perfil × escopo × estado do registro` para contatos,
oportunidades, tarefas, canais, conversas, relatórios, apresentação e contexto
organizacional. Para cada endpoint:

- verifique autenticação e permissão da ação;
- derive tenant da credencial, nunca do input livre;
- valide alcance TENANT/UNIT/OWN e membership vigente;
- valide o registro alvo, inclusive update/delete e relacionamentos recebidos;
- tente IDOR por UUID conhecido, troca de unidade, filtros, paginação, objeto
  relacionado e WebSocket;
- tente mass assignment de tenant, owner, status, timestamps, papel e escopo;
- confirme RLS como defesa adicional com papel runtime restrito.

Inclua casos cross-tenant para leitura, escrita, busca, contagem/relatório,
mensagem, subscription STOMP e respostas de erro. Respostas não devem permitir
enumerar recursos alheios.

### Canais, webhooks e tempo real

- Origem WebSocket em allowlist; token apenas no CONNECT; autorização renovada
  no SUBSCRIBE e nos destinos.
- Credenciais de canal write-only e criptografadas quando persistidas.
- Webhook autentica segredo/assinatura em comparação adequada, limita corpo,
  associa canal ao tenant e persiste antes do sucesso.
- SSRF, redirect, DNS rebinding e exfiltração em conectores HTTP atuais ou
  planejados devem ser classificados corretamente como implementados ou futuros.

### Supply chain e configuração

- Segredos e chaves fora do repositório e logs; profiles de produção falham
  fechados.
- Dependências, plugins de build e imagens fixados/rastreáveis.
- Dois achados altos já declarados no npm não podem ser ignorados nem atualizados
  automaticamente; valide alcance e mitigação.
- Compare controles à versão oficial aplicável do OWASP ASVS e registre a data.

### Saída

Entregue:

1. tabela de ameaças e controles;
2. matriz de autorização por recurso;
3. tabela de testes negativos executados ou ausentes;
4. achados P0–P3 com evidência sanitizada;
5. riscos residuais aceitos/declarados separados dos novos;
6. veredito do Gate B, listando cada evidência que falta.

Não declare vulnerabilidade apenas pelo nome de uma dependência; demonstre versão,
alcance e condição de exploração. Não reduza severidade de falha cross-tenant por
ela exigir UUID conhecido.

## Fim do prompt

