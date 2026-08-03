# Sessão — frontend:F2 casca e navegação

- Data: 2026-08-01
- Branch observada: `main`
- Baseline: `793777d + working-tree`
- Ambiente: Windows, Node.js 24.18.0, npm 11.16.0
- Responsável: Codex

## Entregue

- registro único em `src/app/routes.ts` alimenta o carregamento lazy, o roteador
  e o menu; o mapa paralelo de páginas foi removido;
- capability, entitlement, permissão e apresentação são resolvidos como sinais
  independentes, com decisão verificável por rota;
- fallback de apresentação nunca torna visível uma rota negada;
- casca responsiva com sidebar desktop e disclosure mobile sem modal artificial;
- skip link, ordem de foco, foco inicial no menu e retorno ao botão por Escape;
- ícones do registro aparecem no menu sem substituir o nome acessível;
- menu desktop recolhível com preferência v1 escopada por usuário sem gravar seu
  identificador, sessão, tenant, login ou autorização;
- seletor ausente com um contexto e disponível somente para múltiplas opções
  autorizadas;
- durante troca lenta, o conteúdo anterior é desmontado até o callback limpar
  dados/cache e confirmar o novo contexto; falha mantém mensagem segura;
- rota conhecida negada renderiza 403; rota desconhecida renderiza 404 dentro
  da casca; o alias legado `/oportunidades` redireciona para `/funis`;
- nenhum componente, tema ou dependência externa foi adicionado por antecipação.

## Limites reais do backend

O usuário autenticado atual publica somente um `tenantId`, portanto a aplicação
executável passa um único contexto autorizado e não mostra seletor. O caso de
múltiplas unidades foi exercitado no contrato de componente com dados
sintéticos. O backend ainda não publica listas de capabilities, entitlements ou
permissões; até os Prompts 04/06, os sinais ficam `nao-publicado` e os endpoints
continuam sendo a única autoridade de segurança.

## Evidência

- `npm test`: 12 arquivos, 38 testes aprovados;
- `npm run test:unit`: 17 testes aprovados;
- `npm run test:component`: 21 testes aprovados;
- `npm run lint`: 0 erros e 3 avisos conhecidos de Fast Refresh;
- `npm run build`: passou, 1.868 módulos transformados;
- aplicação completa autenticada com fixtures sintéticas comprovou 404 e casca;
- componente comprovou foco/Escape, contexto único/múltiplo, troca lenta sem
  dados anteriores e preferência sanitizada;
- o JS inicial passou de 279,43 kB na F1 para 298,68 kB nesta fotografia, devido
  à casca e aos ícones usados; F11 fará o budget formal por rota;
- nenhum teste ignorado, em `.only` ou em quarentena.

## Gate e próximo passo

`frontend:F2` está concluído, mas não fecha o Gate C sozinho. `frontend:F3` só
pode iniciar depois de `backend:05` publicar o contrato determinístico exigido.
