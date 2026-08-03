# Prompt — consolidação, veredito e roadmap

## Início do prompt

Consolide os resultados das revisões 02 a 09 do CRM PNP. Não reabra a análise do
zero e não aplique correções. Valide evidências críticas no código, use
`11-MODELO-DE-ACHADO.md` e preserve incertezas declaradas.

### 1. Higienização dos achados

- Una duplicatas pela causa raiz, mantendo todas as evidências e impactos.
- Separe defeito confirmado, vulnerabilidade, desvio de regra/arquitetura,
  evidência ausente, dívida aceita, risco residual e item planejado.
- Reavalie severidade pelo impacto/probabilidade, nunca pelo esforço.
- P0/P1 exigem cenário reproduzível ou cadeia de evidências forte; se faltar,
  mantenha certeza `provável` e indique o teste decisivo.
- Não rebaixe falha cross-tenant, perda de dados ou bypass por exigir conhecimento
  de UUID, timing ou usuário autenticado.

### 2. Efeitos transversais

Crie uma matriz `causa → componentes afetados → jornadas → gates`. Dê atenção a:

- tenant e unidade propagados entre token, aplicação, RLS, WebSocket e relatório;
- diferença entre permissão backend e visibilidade frontend;
- nomes/paginação/nulos/datas/centavos entre DB, API, adapter e tela;
- idempotência entre request, transação, evento, worker e provedor;
- estado declarado em manifest/documentação versus execução real;
- controles existentes mas sem teste com o papel/ambiente correto.

### 3. Veredito de prontidão

Dê um veredito independente para:

- **demonstração interna**;
- **piloto controlado com dados não sensíveis**;
- **piloto com clientes reais**;
- **produção geral**.

Use somente `pronto`, `pronto com condições explícitas` ou `não pronto`. Para
cada um, liste bloqueadores, condições, risco residual e evidência. Não confunda
feature incompleta com risco de segurança, mas considere promessas exibidas na
UI e jornada necessária ao público daquele ambiente.

### 4. Gates A–F

Para cada critério de `contexto/prompts/GATES.md`, marque:

- `comprovado`: evidência atual, reproduzível e suficiente;
- `falhou`: evidência executada contradiz o critério;
- `não verificado`: controle pode existir, mas a prova não foi produzida;
- `aberto por planejamento`: etapa explicitamente ainda não implementada;
- `não aplicável`: justificativa concreta.

Um gate só é `aprovado` se todos os critérios obrigatórios forem comprovados ou
formalmente não aplicáveis. O status do manifest é contexto, não prova.

### 5. Roadmap de correção

Ordene ações por redução de risco e dependência:

1. contenção imediata de P0, se houver;
2. correção da causa e teste de regressão;
3. P1 que bloqueia o próximo ambiente;
4. evidência necessária para fechar gates;
5. P2/P3 agrupados por área quando isso reduzir retrabalho.

Para cada ação informe: achados resolvidos, mudança mínima, dependências,
responsável por papel (produto, backend, frontend, plataforma, segurança ou
jurídico), teste/evidência de saída e risco de rollout. Não invente datas ou
estimativas sem capacidade e histórico fornecidos.

### 6. Relatório final

Estruture o documento assim:

1. veredito executivo em até 12 linhas;
2. baseline, escopo e limitações;
3. contagem por severidade, tipo, certeza e área;
4. top bloqueadores com causa raiz;
5. achados completos P0 → P3;
6. matriz de regras de negócio;
7. mapa de arquitetura e ameaças;
8. divergências DB/API/frontend;
9. resultado de testes, CI, dependências e operação;
10. gates A–F critério por critério;
11. vereditos por ambiente;
12. roadmap ordenado;
13. riscos aceitos e decisões pendentes;
14. apêndice de evidências sanitizadas.

Faça uma revisão final de consistência: todo resumo aponta para achado completo;
todo achado tem evidência; toda ação aponta para achados; todo gate aponta para
provas. Remova generalidades sem impacto verificável.

Salve em `contexto/revisao-tecnica/resultados/AAAA-MM-DD-revisao-integrada.md`.
No chat, informe apenas veredito, contagem por severidade, bloqueadores e caminho.

## Fim do prompt
