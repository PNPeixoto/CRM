# C4 — Contexto do sistema

Verificado em 2026-08-05.

```mermaid
flowchart LR
    operador["Pessoa operadora\nAtendimento e CRM"]
    gestor["OWNER / ADMIN\nGestão do tenant"]
    crm["CRM PNP\nCRM SaaS omnichannel multi-tenant"]
    telegram["Telegram Bot API\nProvedor externo implementado"]

    operador -->|"HTTPS no navegador"| crm
    gestor -->|"HTTPS no navegador"| crm
    crm -->|"envio via HTTPS"| telegram
    telegram -->|"webhook HTTPS"| crm
```

O sistema oferece contatos, tarefas, funil, relatórios e caixa de entrada. A
integração externa executável hoje é Telegram; outros canais pertencem à
evolução do produto e não são representados como concluídos.

Fontes: `contexto/00-projeto.md`, `TelegramWebhookController`,
`TelegramAdapter` e `LiveChatAdapter`.

