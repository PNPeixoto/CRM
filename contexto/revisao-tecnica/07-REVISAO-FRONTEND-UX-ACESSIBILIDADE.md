# Prompt — frontend, UX e acessibilidade

## Início do prompt

Revise o frontend React/TypeScript do CRM PNP em modo somente leitura. Considere
coerência de produto, contrato real, segurança do navegador, estados da
interface, responsividade e WCAG. Leia `00-CONTEXTO-CANONICO.md`, trilha frontend,
README, contrato HTTP, tokens/componentes, rotas, páginas, adapters e testes. Use
o modelo oficial de achado.

### Estado real do produto

- Classifique cada rota como funcional, parcial, placeholder, inacessível ou
  órfã. Valide pela integração, não pelo nome do componente.
- Rotas atualmente anunciadas como prontas merecem jornada ponta a ponta:
  dashboard, inbox, contatos, funis, tarefas, integrações e relatórios.
- Calendário, reservas, ativos, unidades, equipes, automações, campanhas,
  auditoria e configurações podem estar planejados; verifique se a UI comunica
  isso honestamente e não oferece ação falsa.
- Preset/segmento muda apresentação, não autorização. Diferencie capability,
  entitlement, permissão, escopo e item de menu.

### Arquitetura frontend

- Páginas usam modelos e serviços da aplicação; somente adapters HTTP importam
  tipos OpenAPI gerados.
- Cliente HTTP, autenticação, erros e paginação não têm implementações paralelas.
- Estado remoto, estado de formulário e preferência local não são misturados.
- Token sensível fica em memória; armazenamento persistente contém apenas dados
  explicitamente não sensíveis.
- Datas UTC, valores em centavos, nulos e enums são convertidos numa fronteira
  previsível.
- Evite estado derivado duplicado, efeitos com corrida, request após unmount e
  closures obsoletas.

### Jornadas e estados

Exercite, quando possível, desktop e viewport estreito:

1. login correto, inválido, rate limit, MFA obrigatório, sessão expirada e reset;
2. seleção/contexto de unidade e bloqueio de ação fora do escopo;
3. onboarding por segmento e recarga após conclusão;
4. listar, buscar, paginar, criar, editar e excluir contato;
5. criar/mover/ganhar/perder/reabrir oportunidade;
6. criar/concluir/reabrir tarefa;
7. listar conversa, carregar histórico, receber evento e enviar mensagem;
8. dashboard/relatório sem dados, com dados e com erro parcial.

Para cada tela verifique loading inicial, atualização em curso, vazio verdadeiro,
erro recuperável, 401, 403, 404, 409, 429, timeout, offline e sucesso. A UI não
deve transformar ausência de autorização em vazio silencioso nem mostrar detalhe
interno do backend.

### Sessão e segurança do navegador

- Refresh single-flight, fila de requests, cancelamento e logout coerente entre
  abas conforme a etapa implementada.
- CSRF, CORS, CSP, XSS, URLs, HTML rico e redirects.
- Guards de rota melhoram UX, mas toda autorização permanece no backend.
- Erros e telemetria não incluem tokens, conteúdo de mensagem ou dados pessoais.
- Dependências vulneráveis são analisadas por alcance, sem correção automática.

### Acessibilidade e design system

Use WCAG 2.2 AA vigente como referência e registre versão/data. Verifique:

- navegação completa por teclado, ordem de foco e foco visível;
- foco movido/restaurado em modal, drawer, erro e troca de rota;
- landmarks, títulos, labels, nomes acessíveis e semântica de tabela/lista;
- mensagens de validação e status anunciadas sem depender apenas de cor;
- contraste, zoom/reflow, alvo de toque e redução de movimento;
- formulários com erro associado ao campo e resumo quando necessário;
- inbox e kanban operáveis sem drag-and-drop obrigatório;
- tokens e componentes reutilizados sem valores visuais paralelos injustificados.

### Evidência

- Execute lint, testes, build e verificação do contrato.
- Use testes existentes e inspeção manual reproduzível; screenshot sozinha não
  comprova comportamento nem acessibilidade.
- Para cada falha visual, registre viewport, rota, estado, passos e componente.
- Diferencie aviso de Fast Refresh conhecido de erro funcional.

### Saída

Entregue:

- mapa de rotas e maturidade;
- matriz de jornadas/estados;
- auditoria WCAG por critério aplicável, evidência e impacto;
- divergências entre API, adapter e tela;
- achados P0–P3;
- veredito dos Gates B/C e pendências de F4A/F4, sem antecipar conclusão.

Não redesenhe a interface durante a revisão. Recomendações visuais só entram
quando resolvem uma falha de uso, acessibilidade ou coerência comprovada.

## Fim do prompt

