# ADR-0003 — Webhook confirma somente após persistência durável

- Status: accepted
- Data: 2026-08-01
- Referência histórica: `contexto/03-decisoes.md`, entrada de 2026-07-29

## Contexto

Responder sucesso antes de gravar o evento cria perda definitiva se a
persistência falhar. Processar toda a mensagem dentro do request, por outro lado,
aumenta timeout e retries do provedor.

## Decisão

Receber bytes crus com limite, validar assinatura, persistir evento durável e
idempotente e então responder com o sucesso exigido. Interpretação e efeitos
ocorrem de forma assíncrona. Falha de persistência retorna erro transitório.

## Alternativas descartadas

- Responder primeiro: reconhece dado ainda não durável.
- Processar tudo antes de responder: acopla latência/falha do domínio ao webhook.

## Consequências e revisão

A inserção precisa ser curta e indexada. Payload bruto tem acesso/retention
restritos e nunca aparece em log/evidência. Revisar por provedor apenas o status
e timeout de sucesso, não a ordem de durabilidade.
