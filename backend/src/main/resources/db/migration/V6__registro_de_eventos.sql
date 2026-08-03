-- Registro de publicação de eventos do Spring Modulith.
--
-- Descoberto ao rodar as migrations contra um Postgres real pela primeira vez:
-- `spring-modulith-starter-jpa` mapeia a entidade JpaEventPublication, e o
-- `ddl-auto: validate` recusa a subida com "missing table [event_publication]".
-- O jar não traz .sql — o DDL abaixo foi gerado pelo próprio Hibernate
-- (schema-generation) contra a versão 2.1.0, e não escrito de memória.
--
-- PARA QUE SERVE: dar entrega garantida aos eventos entre módulos. O
-- 01-padroes-tecnicos.md exige comunicação por evento "sempre que a operação
-- puder ser assíncrona"; sem este registro, um evento em voo no momento de um
-- deploy some sem deixar rastro. Com ele, fica pendente e é reprocessado.
--
-- Hoje nenhum @ApplicationModuleListener existe — os listeners são síncronos.
-- A tabela entra porque é requisito da dependência já presente, não por
-- antecipação de desenho.

CREATE TABLE event_publication
(
    id                     uuid PRIMARY KEY,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    -- TEXT e não varchar(255): o Hibernate gera 255 por ser o padrão de String
    -- sem tamanho, mas o conteúdo é o evento serializado em JSON e estoura
    -- esse limite com facilidade. A validação de schema do Hibernate compara
    -- o tipo JDBC, e `text` do Postgres também é VARCHAR — então passa.
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    completion_attempts    integer     NOT NULL DEFAULT 0,
    last_resubmission_date timestamptz,
    status                 text,
    CONSTRAINT event_publication_status_valido
        CHECK (status IS NULL OR status IN
               ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

-- Consulta central do registro: o que ainda não foi concluído.
CREATE INDEX event_publication_incompletos
    ON event_publication (completion_date, publication_date)
    WHERE completion_date IS NULL;

CREATE INDEX event_publication_por_listener
    ON event_publication (listener_id, serialized_event);

-- ---------------------------------------------------------------------------
-- Por que esta tabela NÃO tem RLS
-- ---------------------------------------------------------------------------
--
-- Ela não tem tenant_id: é infraestrutura de entrega, escrita e lida pelo
-- próprio framework, fora de qualquer contexto de tenant. Ativar RLS aqui
-- quebraria o registro sem tornar nada mais seguro — o framework não define
-- app.tenant_id.
--
-- ATENÇÃO, e isto é uma consequência real: `serialized_event` guarda o evento
-- inteiro em JSON, e alguns eventos do CRM carregam CONTEÚDO DE MENSAGEM DE
-- CLIENTE (MensagemNormalizadaEvent traz o texto). Ou seja, esta tabela pode
-- conter dado de cliente sem o isolamento que todas as outras têm.
--
-- Enquanto os listeners forem síncronos, nada é gravado aqui. No dia em que
-- o primeiro @ApplicationModuleListener entrar, duas coisas passam a ser
-- obrigatórias:
--   1. arquivamento/expurgo agressivo dos concluídos, para a tabela não virar
--      um histórico permanente de mensagens fora do RLS;
--   2. reavaliar se o evento precisa mesmo carregar o texto, ou se basta o id
--      da mensagem — que é a correção de raiz.
