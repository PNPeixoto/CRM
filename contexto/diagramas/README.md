# Diagramas de arquitetura

Estas visões documentam somente a arquitetura executável. Foram verificadas em
2026-08-05 e seguem o [ADR-0009](../decisoes/ADR-0009-diagramas-c4.md).

| Visão | Pergunta respondida | Fontes principais |
|---|---|---|
| [Contexto](c4-contexto.md) | Quem usa o CRM e com qual sistema externo ele fala? | produto, controllers de webhook |
| [Containers](c4-containers.md) | Quais processos e datastores existem hoje? | Compose, POM, Vite |
| [Componentes de identidade](c4-componentes-identidade.md) | Como login, MFA e sessão se compõem? | módulo `identity` |
| [Sequência de login/MFA](sequencia-login-mfa.md) | Como ocorre o acesso comum e o primeiro cadastro? | controllers, serviços e SPA |

## Convenções

- seta contínua: chamada ou tráfego em runtime;
- cilindro: armazenamento;
- bloco tracejado: limite lógico dentro de um processo;
- nenhum bloco representa algo “planejado” sem legenda explícita.

O mapa não concede autorização nem define contrato. Em divergência, código,
configuração executável e ADR vigente vencem o desenho.

