# C4 — Componentes do módulo de identidade

Verificado em 2026-08-05.

```mermaid
flowchart LR
    spa["SPA / adaptador HTTP"]
    tenantApi["tenant.api\nTenantLookup"]
    orgApi["organization.api\nOrganizationAccess"]
    db[("PostgreSQL\nusuários, MFA e refresh")]
    redis[("Redis\nbloqueio progressivo")]

    subgraph identity["Módulo identity"]
        controller["AuthController\n/endpoints /api/auth"]
        auth["AutenticacaoService\norquestra credenciais e sessão"]
        password["PasswordEncoder + Policy\nArgon2id e pepper"]
        mfa["MfaService + cipher\nTOTP e recovery codes"]
        tokens["AccessTokenService\nJWT HS256"]
        refresh["RefreshTokenService\nrotação, família e reuso"]
        bloqueio["BloqueioProgressivoService"]
        users["AppUserRepository"]
    end

    spa --> controller
    controller --> auth
    auth --> tenantApi
    auth --> password
    auth --> mfa
    auth --> tokens
    auth --> refresh
    auth --> bloqueio
    auth --> users
    mfa --> orgApi
    users --> db
    mfa --> db
    refresh --> db
    bloqueio --> redis
```

O access token sai no corpo e permanece apenas em memória na SPA. O refresh
fica em cookie `HttpOnly`, `SameSite=Strict`, restrito a `/api/auth`. O módulo
usa somente as interfaces públicas de `tenant` e `organization`; nenhum
`internal` externo é importado.

Fontes: `AuthController`, `AutenticacaoService`, `MfaService`,
`RefreshTokenService`, `BloqueioProgressivoService` e `AuthContext.tsx`.

