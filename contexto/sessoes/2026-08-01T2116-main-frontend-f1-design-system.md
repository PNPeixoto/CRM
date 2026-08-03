# Sessão — frontend:F1 design system e tokens

- Data: 2026-08-01
- Branch observada: `main`
- Baseline: `793777d + working-tree`
- Ambiente: Windows, Node.js 24.18.0, npm 11.16.0
- Responsável: Codex

## Entregue

- primitivas privadas `--palette-*` separadas dos tokens semânticos consumidos
  pela interface;
- mapas claro e escuro completos, preservando claro como padrão e sem criar
  seletor ou terceiro tema;
- `--border-control` com contraste não textual mínimo de 3:1 nos dois temas;
- foco visível global e redução de animação/transição por preferência do
  sistema;
- botão com tipo seguro por padrão, estados disabled e variantes existentes;
- input, label, alerta e nova primitiva de select nativo com erro e disabled;
- skeleton com anúncio acessível separado da forma visual;
- estado único para loading, vazio inicial, filtro sem resultado, erro, sem
  permissão e offline;
- dashboard e relatórios usando o estado de erro; contatos e tarefas distinguem
  ausência inicial de filtro sem resultado;
- erro do login associado aos três campos por `aria-describedby`;
- correção do utilitário `text-muted` sem mapeamento no placeholder comum.

## Escopo proporcional

Não foi adicionada dependência. Avatar, tooltip, toast, menu composto e modal
não possuem consumidor nesta etapa e não foram antecipados. Tabela, drawer,
tabs, breadcrumbs e seleção avançada permanecem vinculados à primeira tela que
realmente os exigir. JetBrains Mono segue sem uso decorativo e reservada a dado
técnico ou identificador.

O contrato automatizado lê os tokens executáveis e valida os pares de contraste
claro/escuro. Axe em jsdom continua com contraste desabilitado porque não possui
layout real; teclado, zoom, reflow e leitores de tela completos permanecem como
prova de navegador em F2/F10.

## Evidência

- `npm test`: 9 arquivos, 28 testes aprovados;
- `npm run test:unit`: 13 testes aprovados;
- `npm run test:component`: 15 testes aprovados;
- `npm run lint`: 0 erros e 3 avisos de Fast Refresh já conhecidos;
- `npm run build`: passou, 1.867 módulos transformados;
- contrato rejeita literal de cor fora da paleta, paleta privada em componente,
  escala Tailwind de cor, contraste insuficiente e tema escuro incompleto;
- testes de componente cobrem comportamento, disabled, erro, temas, estados e
  acessibilidade automatizada;
- nenhum teste ignorado, em `.only` ou em quarentena.

## Gate e próximo passo

`frontend:F1` está concluído, mas não fecha o Gate C sozinho. O próximo passo é
`frontend:F2`, responsável pela casca e navegação responsivas; jornadas P0 e
auditorias posteriores ainda completam o gate.
