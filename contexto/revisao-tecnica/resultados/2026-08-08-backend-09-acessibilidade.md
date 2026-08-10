# Auditoria de acessibilidade — backend:09 / casca do CRM PNP

- Padrão: WCAG 2.2 AA
- Data: 2026-08-08
- Escopo: login, primitivas, estados de conteúdo, navegação e funil

## Resumo

Um achado material foi encontrado e corrigido: controles principais tinham
40 px. Tokens, botões, inputs, selects e ações da casca passaram a oferecer
44 px; o checkbox de tarefa recebeu um rótulo clicável de 44 px.

Não foi criada outra casca. A consolidação referencia e revalida F1/F2, passa a
consumir `/api/organizacao/contextos` e preserva o backend como barreira de
autorização.

## Evidências

| Área | Evidência | Resultado |
|---|---|---|
| semântica | axe-core nos componentes e smoke da aplicação | sem violações automatizadas |
| contraste | contrato calcula texto 4,5:1 e UI/foco 3:1 nos temas claro/escuro | aprovado |
| foco/movimento | `:focus-visible`, skip link e `prefers-reduced-motion` por contrato | aprovado |
| teclado da casca | foco inicial no menu, Escape devolve foco, 403/404 e troca desmonta outlet | aprovado por componente |
| funil | movimento por `select` nativo, sem depender de drag-and-drop | aprovado |
| estados | carregando, vazio, sem resultado, erro, sem permissão, 404 e offline | aprovados por componente |
| autenticação | heading, labels, nomes acessíveis, erro ligado aos campos e foco visível | aprovado |
| viewport móvel | navegador real em 320 × 568: `scrollWidth = clientWidth = 320` | sem perda horizontal |
| alvos no login | navegador real mediu empresa/login/senha/botão em 44 px | aprovado |
| status | ganho/perda, atraso, canal e conexão têm texto além da cor | aprovado |
| acesso | permissões reais dirigem menu/guarda; backend continua protegendo API | aprovado |

## Contraste verificado

O teste cobre `text-strong`, `text-muted`, textos sobre marca/shell e estados
success/danger/warning/info. `border-control` e `focus-ring` têm mínimo 3:1.
Literais de cor permanecem restritos às primitivas `--palette-*`.

## Riscos residuais

- NVDA/VoiceOver não foi executado nesta sessão. A árvore acessível do login e
  axe estão limpos, mas uma jornada manual com leitor de tela continua
  obrigatória no `frontend:F10` antes de produção.
- O controle disponível não confirmou zoom nativo a 200%. Reflow mais estreito
  foi medido em 320 px sem overflow; zoom 200% permanece item manual do F10.
- A casca autenticada foi revalidada por teste de componente com dados
  sintéticos. O backend local não pôde ser iniciado nesta sessão porque Docker
  estava indisponível.
- Capabilities e entitlements continuam `nao-publicado`; não foram inventados
  no cliente. Entitlements pertencem ao backend:19 e o endpoint é a barreira.

Nenhum dos resíduos justifica reintroduzir outra biblioteca visual. Eles são
provas manuais de produção, não lacunas arquiteturais da casca.
