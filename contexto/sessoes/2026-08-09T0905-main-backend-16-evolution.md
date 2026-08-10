# Sessão — Backend 16 e continuidade Evolution

- Data: 2026-08-09 09:05 (America/Montevideo)
- Branch observada: `main`
- Escopo: Prompt backend 16 e laboratório multicanal Evolution

## Entregue

- Migration V21 com conectores aprovados, credenciais cifradas, tentativas
  sanitizadas, integridade composta e RLS forçado.
- Ação versionada `HTTP_CONNECTOR_V1`, ligada ao motor por `connectorId`, com
  idempotência determinística e replay convergente.
- Anti-SSRF para IPv4/IPv6, metadata, DNS misto e rebinding; redirects
  desativados e resolução fixada durante a conexão.
- Cliente dedicado sem cookies/retry automático, TLS 1.2/1.3, timeout, teto de
  resposta, concorrência e orçamento por tenant/conector.
- Templates finitos com escape de URL/JSON e sem linguagem executável.
- Segredo AES-256-GCM write-only, resolvido somente no envio; preview e
  diagnóstico não carregam corpo, token ou URL renderizada.
- Produção fail-closed sem proxy de egress; configuração documentada.
- OpenAPI e tipos do frontend atualizados. Prompt 16 concluído e Gate D fechado.

## Evidências

- Backend: 187 testes, 0 falhas, incluindo banco vazio e V8→V21 em PostgreSQL
  17 real.
- Conector: SSRF IPv4/IPv6, metadata, DNS rebinding, redirect, resposta grande,
  timeout, fuzz de template, segredo cifrado e retry idempotente exercitados.
- Frontend: 128 testes, build e `api:check` verdes.
- Lint: somente três avisos preexistentes de Fast Refresh.
- Imagem local reconstruída sem apagar volumes; readiness `UP`, Flyway
  estrutural `21:true`, três contas preservadas e permissões de integração
  aplicadas.

## Evolution

- API 2.3.7, PostgreSQL e Redis próprios permanecem ativos.
- A instância `pnp-teste` e o webhook continuam configurados.
- Consulta sanitizada confirmou estado `connecting`; novo QR temporário foi
  emitido. A leitura no aparelho e a prova real de entrada/saída seguem como
  ação humana pendente.

## Próximo passo

1. Confirmar `open` na Evolution e provar mensagem recebida/enviada na Inbox.
2. Executar `backend:17` (auditoria). F9 continua condicionado a volume real.
