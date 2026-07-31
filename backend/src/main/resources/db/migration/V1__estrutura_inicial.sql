-- Estrutura inicial: tenant, app_user, refresh_token.
--
-- Convenções aplicadas aqui e válidas para toda tabela criada depois:
--   * chave primária UUID gerada NA APLICAÇÃO (v7). O docker-compose fixa
--     postgres:17-alpine e uuidv7() só existe do Postgres 18 em diante, então
--     não há DEFAULT — quem não passar o id recebe erro de NOT NULL, que é o
--     comportamento desejado (falhar cedo e alto).
--   * timestamps em TIMESTAMPTZ, sempre UTC.
--   * exclusão lógica em deleted_at; exclusão física só no expurgo por retenção.
--   * RLS ativo e FORÇADO em toda tabela com tenant_id.

-- ---------------------------------------------------------------------------
-- Contexto de tenant
-- ---------------------------------------------------------------------------

-- Lê o tenant da transação corrente. O segundo argumento `true` faz
-- current_setting devolver NULL em vez de erro quando a variável nunca foi
-- definida — é o que permite que a política falhe FECHADA: sem tenant no
-- contexto, `tenant_id = NULL` avalia para NULL, que o RLS trata como "não
-- visível". Esquecer o SET LOCAL não vaza dado, apenas devolve vazio.
--
-- NULLIF cobre o caso de a variável existir com string vazia, em que o cast
-- direto para uuid lançaria erro.
CREATE FUNCTION current_tenant_id() RETURNS uuid
    LANGUAGE sql
    STABLE
AS $$
SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;

COMMENT ON FUNCTION current_tenant_id() IS
    'Tenant da transação corrente, definido por SET LOCAL app.tenant_id. '
        'NULL quando ausente, o que torna as políticas de RLS fechadas por padrão.';

-- Mantém updated_at correto mesmo em UPDATE feito fora da aplicação (correção
-- manual, script de manutenção). Deixar isso só no Hibernate faria a coluna
-- mentir exatamente nas ocasiões em que ela é mais consultada.
CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- tenant
-- ---------------------------------------------------------------------------

CREATE TABLE tenant
(
    id         uuid PRIMARY KEY,
    -- Identificador estável usado na tela de login e, no futuro, no subdomínio.
    -- Separado do nome porque o nome comercial muda e o slug não pode mudar
    -- sem quebrar login de todos os usuários da conta.
    slug       text        NOT NULL,
    name       text        NOT NULL,
    active     boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- Sem FK para app_user: é registro de auditoria, e a FK impediria o
    -- expurgo de um usuário sem reescrever o histórico de quem criou o quê.
    created_by uuid,
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT tenant_slug_formato CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$')
);

-- Slug reaproveitável depois da exclusão lógica, mas único entre os ativos.
CREATE UNIQUE INDEX tenant_slug_unico ON tenant (slug) WHERE deleted_at IS NULL;

CREATE TRIGGER tenant_updated_at
    BEFORE UPDATE
    ON tenant
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- A tabela tenant não tem coluna tenant_id: ela É o tenant, e a política
-- compara a própria PK. Sem isso, qualquer usuário autenticado poderia
-- enumerar a carteira de clientes da plataforma.
ALTER TABLE tenant ENABLE ROW LEVEL SECURITY;
-- FORCE é o que faz o RLS valer também para o DONO da tabela. O usuário `crm`
-- do docker-compose é dono do banco e das tabelas; sem FORCE, o Postgres
-- ignora silenciosamente toda política para ele e o isolamento vira decoração.
ALTER TABLE tenant FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolamento ON tenant
    USING (id = current_tenant_id())
    WITH CHECK (id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- app_user
-- ---------------------------------------------------------------------------

CREATE TABLE app_user
(
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    login         text        NOT NULL,
    email         text        NOT NULL,
    full_name     text        NOT NULL,
    -- Argon2id em formato PHC ($argon2id$v=19$m=...). Nunca a senha, nunca
    -- um hash reversível. O tamanho é livre porque os parâmetros do Argon2
    -- podem mudar e a string codificada cresce junto.
    password_hash text        NOT NULL,
    active        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    deleted_at    timestamptz
);

-- Login é único POR TENANT, não global: dois franqueados diferentes podem ter
-- um "joao". O índice começa por tenant_id porque é assim que ele também
-- serve as consultas de listagem, conforme a regra de índice composto.
--
-- LOWER porque login e e-mail são insensíveis a maiúsculas na expectativa de
-- quem digita. Sem isso, "Joao" e "joao" viram DOIS usuários no mesmo tenant,
-- e o login passa a depender de o usuário lembrar como digitou no cadastro —
-- com a resposta de erro uniforme, ele não teria como descobrir.
--
-- O índice precisa ser sobre LOWER(...) e não só a consulta: uma função
-- aplicada à coluna na cláusula WHERE faz o planejador ignorar um índice
-- comum, e a unicidade deixaria de valer entre grafias diferentes.
CREATE UNIQUE INDEX app_user_login_unico_por_tenant
    ON app_user (tenant_id, lower(login)) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX app_user_email_unico_por_tenant
    ON app_user (tenant_id, lower(email)) WHERE deleted_at IS NULL;

CREATE TRIGGER app_user_updated_at
    BEFORE UPDATE
    ON app_user
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_user FORCE ROW LEVEL SECURITY;

CREATE POLICY app_user_isolamento ON app_user
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- refresh_token
-- ---------------------------------------------------------------------------

CREATE TABLE refresh_token
(
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    user_id       uuid        NOT NULL REFERENCES app_user (id),
    -- Todos os tokens descendentes de um mesmo login compartilham a família.
    -- Reuso de um token já rotacionado revoga a família inteira: é o sinal de
    -- que alguém copiou o token, e não há como distinguir o ladrão da vítima.
    family_id     uuid        NOT NULL,
    -- SHA-256 do valor aleatório de 256 bits, em hex (64 caracteres). O valor
    -- em claro existe apenas no cookie do usuário. Um dump do banco não
    -- permite forjar sessão. Não é Argon2 de propósito: o segredo já tem
    -- entropia máxima, então não há ataque de dicionário a encarecer, e o
    -- refresh precisa ser barato o bastante para rodar a cada 15 minutos.
    token_hash    char(64)    NOT NULL,
    issued_at     timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    -- Marcado no momento da rotação. Token com used_at preenchido que volta a
    -- ser apresentado é reuso.
    used_at       timestamptz,
    revoked_at    timestamptz,
    revoked_reason text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    -- Presente por uniformidade com as demais tabelas, mas a revogação de um
    -- token é revoked_at, não deleted_at. deleted_at aqui é só o expurgo por
    -- retenção de tokens expirados.
    deleted_at    timestamptz
);

-- Único global: o hash é sorteado com 256 bits de entropia, colisão entre
-- tenants é impossível na prática, e a unicidade global impede que um token
-- válido em um tenant seja apresentado em outro.
CREATE UNIQUE INDEX refresh_token_hash_unico ON refresh_token (token_hash);

-- Revogação da família inteira na detecção de reuso.
CREATE INDEX refresh_token_familia ON refresh_token (tenant_id, family_id);

-- Sessões ativas de um usuário e revogação em massa no logout.
CREATE INDEX refresh_token_usuario ON refresh_token (tenant_id, user_id);

-- Expurgo por retenção varre por data e ignora o tenant de propósito.
CREATE INDEX refresh_token_expiracao ON refresh_token (expires_at);

CREATE TRIGGER refresh_token_updated_at
    BEFORE UPDATE
    ON refresh_token
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

ALTER TABLE refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token FORCE ROW LEVEL SECURITY;

CREATE POLICY refresh_token_isolamento ON refresh_token
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- Resolução de tenant no login
-- ---------------------------------------------------------------------------

-- O login é a única operação anterior ao contexto de tenant: para saber qual
-- é o tenant, é preciso consultar a tabela tenant, que está sob RLS e por
-- isso devolveria vazio.
--
-- SECURITY DEFINER faz esta função rodar com os privilégios do dono, atravessando
-- o RLS. É deliberadamente a MENOR brecha possível: recebe um slug e devolve
-- um único uuid. Não lista, não devolve nome, não aceita filtro. Quem chamar
-- em laço com slugs aleatórios só descobre quais contas existem — o mesmo que
-- a tela de login já revela — e não obtém nenhum dado de dentro delas.
--
-- search_path fixo é obrigatório em SECURITY DEFINER: sem ele, o chamador
-- pode criar uma tabela `tenant` em um schema próprio, colocá-la na frente do
-- search_path e fazer a função privilegiada ler o objeto dele.
CREATE FUNCTION resolve_tenant_id_por_slug(p_slug text) RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
SELECT id
FROM tenant
WHERE slug = p_slug
  AND active
  AND deleted_at IS NULL
$$;

COMMENT ON FUNCTION resolve_tenant_id_por_slug(text) IS
    'Único caminho legítimo para obter um tenant_id sem contexto de tenant. '
        'Usado apenas pelo login. Não expõe nenhum atributo além do id.';

-- O refresh tem o mesmo problema do login, por outro caminho: o access token
-- já expirou e a única credencial apresentada é o cookie de refresh. Sem
-- tenant no contexto, a política de refresh_token não casa com linha nenhuma
-- e a renovação de sessão nunca funcionaria.
--
-- A alternativa seria embutir o tenant no próprio valor do cookie. Foi
-- descartada: colocaria um tenant_id vindo do cliente no caminho, e ainda que
-- ele fosse conferido depois, passaria a existir um lugar no sistema onde
-- essa origem é aceita. Uma função que exige o segredo inteiro para responder
-- não abre essa porta.
--
-- A entrada é o SHA-256 do segredo de 256 bits: quem não tem o token não
-- consegue nem formular a pergunta.
CREATE FUNCTION resolve_tenant_id_por_refresh_hash(p_token_hash text) RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
SELECT tenant_id
FROM refresh_token
WHERE token_hash = p_token_hash
$$;

COMMENT ON FUNCTION resolve_tenant_id_por_refresh_hash(text) IS
    'Resolve o tenant de um refresh token para que o RLS possa ser aplicado na '
        'sequência. Não valida o token: expiração, uso e revogação são '
        'verificados depois, já sob a política de isolamento.';
