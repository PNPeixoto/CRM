# Sessão 2026-08-01T1915 — main — Prompt 01 Docker e ambientes

## Objetivo

Tornar build e desenvolvimento reproduzíveis, separar dev/test/prod e produzir
imagem mínima sem aproximar defaults locais da produção.

## Resultado

- Imagem multi-stage Temurin 25 criada e validada com UID/GID 10001.
- Compose de desenvolvimento sobe app, PostgreSQL 17 e Redis 7 com health
  checks, volume nomeado e hardening do app.
- Profiles dev/test/prod separados; produção sem variáveis falha fechada.
- Windows, Linux, promoção e rollback por tag imutável documentados.
- Inicialização do zero passou com projeto/volume descartáveis; somente esse
  volume foi apagado. O ambiente persistente foi restaurado saudável.
- Evidência detalhada em `contexto/diagnosticos/2026-08-01-prompt-01.md`.

## Verificações

- Backend: 59/59 testes passaram.
- Frontend: 6/6 testes e build passaram; lint sem erros, 3 avisos.
- Redis ativo: liveness/readiness `UP`; Redis parado: liveness 200 e readiness
  503; depois da restauração: readiness 200.
- Runtime: não-root, raiz somente leitura, sem capabilities e com limites.
- Imagem: sem fonte, Maven, JDK, cache ou assinatura comum de segredo.
- Produção: saída 1 sem `DB_URL`.
- Compose e whitespace: válidos.

## Scan e encerramento

- O scan da imagem `r2` encontrou 0 críticas e 1 alta: CVE-2026-54291 no
  pgJDBC 42.7.11.
- O `pom.xml` passou a fixar pgJDBC 42.7.12, versão corrigida upstream.
- A suíte permaneceu em 59/59 e a correção foi publicada na tag imutável `r3`.
- O scan final da `r3` analisou 222 pacotes: 0 críticas, 0 altas e nenhum pacote
  vulnerável nessas severidades.
- O Prompt 01 foi marcado `completed`; o Prompt 02 é o próximo.

Nenhum segredo, cookie, payload, conteúdo de mensagem ou dado pessoal foi
registrado.
