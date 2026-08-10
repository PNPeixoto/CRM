# Sequência — Login e primeiro cadastro de MFA

Verificado em 2026-08-05.

```mermaid
sequenceDiagram
    actor Pessoa
    participant SPA
    participant API as API identity
    participant Redis
    participant DB as PostgreSQL + RLS
    participant Authenticator as Aplicativo autenticador

    Pessoa->>SPA: empresa, login e senha
    SPA->>API: POST /api/auth/login
    API->>Redis: consultar limite por origem/credencial
    API->>DB: resolver tenant e usuário ativo
    API->>API: verificar Argon2id + pepper

    alt MFA já ativo
        API-->>SPA: MFA_NECESSARIO quando falta código
        Pessoa->>Authenticator: ler TOTP
        Pessoa->>SPA: código TOTP ou recovery code
        SPA->>API: POST /api/auth/login com codigoMfa
        API->>DB: validar uso único e abrir sessão
        API-->>SPA: access token + cookie refresh HttpOnly
    else MFA obrigatório ainda não cadastrado
        API-->>SPA: MFA_CADASTRO_NECESSARIO
        SPA->>API: POST /api/auth/mfa/enrollment com credenciais
        API-->>SPA: desafio + segredo/otpauth temporários
        Pessoa->>Authenticator: cadastrar segredo e ler TOTP
        SPA->>API: POST /api/auth/mfa/activation
        API->>DB: persistir segredo cifrado e recovery codes por hash
        API-->>SPA: sessão + códigos de recuperação uma única vez
    else MFA não obrigatório
        API-->>SPA: access token + cookie refresh HttpOnly
    end
```

Erros de credencial não revelam se empresa ou login existem. O tenant é
resolvido no servidor e não é aceito como identificador interno enviado pelo
cliente.
