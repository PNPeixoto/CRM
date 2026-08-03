---
id: "F4A"
canonical_id: "frontend:F4A"
title: "Segurança do navegador e supply chain"
phase: "frontend_security"
risk: "critical"
prerequisites: ["frontend:F0A", "frontend:F3"]
blocking: "before-external-pilot"
produces: ["CSP verificável", "defesas CSRF/XSS", "pipeline de dependências"]
gate: "B"
---

# F4A — Segurança do navegador e supply chain

## Objetivo

Reduza a superfície específica do navegador antes de expor o sistema a usuários
externos, com controles testáveis e sem depender apenas de `SameSite`.

## Trabalho

1. Proíba `eval`, `new Function` e HTML arbitrário. Renderize conteúdo externo
   como texto. Se existir caso excepcional de `dangerouslySetInnerHTML`, exija
   sanitização revisada, teste de regressão e justificativa registrada.
2. Permita apenas esquemas de URL necessários; use `noopener,noreferrer` em nova
   aba e sanitize SVG externo ou sirva-o como arquivo sem execução.
3. Implante CSP estrita, primeiro em `Report-Only` quando necessário e depois
   aplicada: sem `unsafe-eval`, sem inline não autorizado, com `frame-ancestors`
   e destinos mínimos. Sanitize relatórios e defina acesso/retenção.
4. Avalie Trusted Types em modo de relatório. Só torne obrigatório quando a
   compatibilidade e os consumidores estiverem comprovados.
5. Modele CSRF para a topologia real: validação de origem/referer e token quando
   aplicável em refresh, logout e escritas. CORS usa allowlist explícita.
6. Não grave token, PII, conversas, respostas de API ou estado sensível em
   `localStorage`, `sessionStorage`, IndexedDB, URL, histórico ou log do browser.
7. Fixe runtime e gerenciador de pacotes, preserve lockfile e use instalação
   reprodutível na CI. Revise scripts de instalação e mantenha dependências pequenas.
8. Execute análise de vulnerabilidades e segredos com política documentada de
   severidade/exceção. Vincule o artefato ao commit e mantenha source maps privados.
9. Teste vetores XSS, URLs perigosas, CSP, CSRF, CORS e ausência de dados sensíveis
   nas persistências suportadas.

## Aceite

- CSP pode ser verificada no ambiente implantado e não admite execução genérica;
- operações autenticadas não confiam somente em cookie `SameSite` contra CSRF;
- nenhuma persistência do navegador contém token ou PII;
- instalação pela CI é reprodutível e scans têm regra de bloqueio/exceção;
- source maps de produção não são publicados como ativos públicos.

