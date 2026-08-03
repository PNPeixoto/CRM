# Sessão — frontend:F0 diagnóstico

- Data: 2026-08-01
- Branch observada: `main`
- Baseline: `793777d + working-tree`
- Ambiente: Windows, Node.js 24.18.0, npm 11.16.0
- Responsável: Codex

## Escopo

Executado o diagnóstico definido em `frontend:F0`, sem corrigir código,
atualizar dependências ou alterar serviços. A working tree já continha mudanças
extensas; todas foram preservadas.

## Resultado

- stack, scripts, dependências, rotas, páginas e componentes inventariados;
- 19 entradas de navegação/fluxo classificadas: uma rota técnica pronta, nove
  jornadas parciais e nove placeholders;
- tokens, fontes, temas e responsividade comparados com o briefing disponível;
- páginas mapeadas aos endpoints e ao estado real dos controllers;
- armazenamento, cookies por função, CSP, CORS, HTML externo, source maps,
  telemetria e supply chain inspecionados sem registrar valor sensível;
- suporte de navegador ficou explicitamente pendente porque não há contrato,
  telemetria ou matriz versionada;
- relatório produzido em `contexto/frontend-inventario.md`.

## Verificações

- `npm ls --depth=0`: passou;
- `npm run lint`: passou com 0 erros e 3 avisos de Fast Refresh;
- `npm test`: 3 arquivos, 6 testes, todos aprovados;
- `npm run build`: passou, 1.864 módulos transformados;
- build sem source maps e com lazy chunks por página.

## Conclusão

O cliente é uma alpha funcional com APIs reais, mas os estados `pronto` do
registro significam apenas “não placeholder”. O próximo prompt frontend é F0A,
que deve estabelecer a base de testes antes de F1/F2. Contrato OpenAPI, sessão,
segurança do navegador e telas de domínio permanecem nos prompts próprios.
