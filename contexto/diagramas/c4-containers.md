# C4 — Containers

Verificado em 2026-08-05.

```mermaid
flowchart LR
    pessoa["Pessoa usuária"]
    telegram["Telegram Bot API"]

    subgraph crm["CRM PNP"]
        spa["SPA React 19\nTypeScript, Vite, Tailwind"]
        api["Backend modular\nJava 25, Spring Boot 4.1\nREST + broker STOMP simples em memória"]
        postgres[("PostgreSQL 17\nFlyway + RLS")]
        redis[("Redis 7\nlimites e estado efêmero")]
    end

    pessoa -->|"usa no navegador"| spa
    spa -->|"REST /api, JSON, JWT + CSRF"| api
    spa <-->|"WebSocket STOMP /ws"| api
    api -->|"JDBC runtime restrito"| postgres
    api -->|"Flyway por conexão administrativa"| postgres
    api -->|"protocolo Redis"| redis
    api -->|"HTTPS de saída"| telegram
    telegram -->|"webhook HTTPS"| api
```

O Compose local executa backend, PostgreSQL e Redis. A SPA roda pelo Vite no
desenvolvimento e produz artefatos estáticos no build; o servidor de produção
que os publicará não está definido aqui. O broker em memória impede escala
horizontal segura e é objeto do Prompt 28 — RabbitMQ ainda não é container do
sistema.

Fontes: `docker-compose.yml`, `backend/pom.xml`, `frontend/package.json`,
`frontend/vite.config.ts`, `DataSourceConfig` e `WebSocketConfig`.
