---
id: "F13"
canonical_id: "frontend:F13"
title: "Telemetria e tratamento de erros"
phase: "frontend_quality"
risk: "high"
prerequisites: ["backend:18", "backend:22", "frontend:F7"]
blocking: "before-production"
produces: ["error boundaries", "telemetria minimizada", "source maps privados"]
gate: "E"
---

# F13 — Telemetria e erros

## Objetivo

Detecte falhas acionáveis sem transformar observabilidade em coleta paralela de
dados pessoais ou expor código-fonte de produção.

## Trabalho

1. Implemente error boundaries por região/rota e recuperação para falha de chunk,
   sem esconder erros de autorização ou contrato como erro genérico.
2. Capture erros globais, promises rejeitadas, falhas de socket e violações de
   contrato com correlação ao backend, release e ambiente.
3. Use allowlist fechada: código estável, rota sem query/hash, release,
   trace/request ID, major do browser, classe de viewport, estado online, flag
   aprovada, etapa/duração e stack sanitizada quando autorizada.
4. Nunca envie PII, mensagem/conversa, query/hash, body, valor de formulário,
   cookie, token, cabeçalho de autorização, screenshot ou replay de sessão.
5. Mantenha source maps privados, ligados ao release, com acesso e retenção
   restritos; não os publique junto aos ativos do site.
6. Defina amostragem, ambiente, retenção, acesso, base legal e procedimento de
   exclusão conforme backend:22. Alertas exigem dono e ação.
7. Teste o payload realmente enviado e prove a ausência de dados proibidos,
   inclusive em erros contendo URLs, formulários e respostas do servidor.

## Aceite

- uma falha de UI produz evento correlacionável e seguro;
- teste de allowlist reprova campo novo não aprovado;
- source maps não estão acessíveis publicamente;
- retenção, acesso e exclusão têm responsáveis definidos;
- usuário recebe recuperação útil sem detalhe interno ou dado de outro contexto.

