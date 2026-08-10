# ADR-0012 — Conector HTTP, egress e templates limitados

- Status: accepted
- Data: 2026-08-09

## Contexto

O Prompt 16 permite que uma automação chame um sistema externo. Uma URL livre
transformaria a aplicação em proxy para a rede interna e para serviços de
metadata. Uma linguagem de expressão genérica criaria execução remota. Retry,
redirect, DNS rebinding e diagnóstico com corpo completo também poderiam
duplicar efeitos ou expor credenciais e dados de clientes.

## Decisão

O sistema persiste conectores aprovados, não requisições arbitrárias. A origem
é HTTPS, estática e sem usuário, caminho, query ou fragmento. O passo da
automação recebe somente `connectorId`; método, origem, caminho, corpo, limites
e cabeçalhos pertencem à versão aprovada do conector.

São aceitos apenas `GET`, `POST`, `PUT` e `PATCH`, conteúdo JSON e cabeçalhos
restritos. Métodos mutáveis exigem um cabeçalho de idempotência. Templates
fazem substituição finita de seis identificadores técnicos, com escape próprio
para URL e JSON. Não existe `eval`, shell, JavaScript, SpEL ou acesso a classe,
arquivo e rede pelo template.

Antes de conectar, o executor resolve todos os endereços A e AAAA. Se qualquer
resultado for privado, reservado, loopback, link-local, multicast, metadata ou
um bloco IPv6 especial, o destino inteiro é recusado. O snapshot DNS aprovado
é fixado no cliente durante a conexão para impedir rebinding. Redirects ficam
desativados.

Cada chamada usa cliente dedicado, sem cookies, redirect, retry automático ou
descompressão, com TLS 1.2/1.3, timeout, limite de resposta e conexão única.
Concorrência e orçamento são limitados por tenant e conector. Produção exige
um proxy de egress e falha na inicialização se ele não estiver configurado; o
proxy precisa repetir a política de destino e o pinning no ponto onde resolve
o `CONNECT`.

Credenciais usam AES-256-GCM com chave exclusiva e AAD de tenant/conector. São
decifradas somente imediatamente antes do envio e nunca retornadas pela API.
A tentativa persiste hashes, status, código sanitizado, tamanho e duração —
nunca request, response, segredo ou URL renderizada.

## Consequências

- Um usuário não consegue escolher destino, verbo ou cabeçalho no passo da
  automação.
- Um registro DNS misto, redirect ou falha da política de egress é recusado de
  forma conservadora.
- Retry de método mutável usa a mesma chave determinística; replay já concluído
  não chama o destino novamente.
- Diagnóstico perde o corpo remoto de propósito e depende de correlação,
  código sanitizado e métricas.
- Desenvolvimento pode conectar diretamente ao IP validado e fixado; produção
  não sobe sem o proxy de egress.

## Alternativas descartadas

- URL livre por automação: seria SSRF/proxy genérico.
- Allowlist apenas por hostname: não cobre DNS misto nem rebinding.
- Seguir redirect e validar só a primeira URL: permitiria salto para metadata.
- Cliente HTTP compartilhado: poderia herdar cookies, redirects ou estado.
- SpEL/JavaScript para template: ampliaria a superfície para execução remota.
- Persistir request/response completos: vazaria dados e credenciais na trilha.

## Evidências

`backend/src/main/resources/db/migration/V21__conector_http_seguro.sql`,
`backend/src/main/java/br/com/pnp/crm/integration/internal/` e
`backend/src/test/java/br/com/pnp/crm/integration/internal/`.
