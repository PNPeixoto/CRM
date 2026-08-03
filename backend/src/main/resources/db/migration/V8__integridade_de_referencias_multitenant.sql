-- Toda referência de negócio carrega o tenant junto da chave relacionada.
-- Uma FK simples por id confirma que a linha existe, mas não que pertence à
-- mesma empresa. As FKs compostas abaixo tornam esse vazamento impossível no
-- banco, inclusive em caminhos de escrita que ainda não existam na API.

ALTER TABLE app_user ADD CONSTRAINT app_user_tenant_id_unico UNIQUE (tenant_id, id);
ALTER TABLE channel_connection ADD CONSTRAINT channel_connection_tenant_id_unico UNIQUE (tenant_id, id);
ALTER TABLE conversation ADD CONSTRAINT conversation_tenant_id_unico UNIQUE (tenant_id, id);
ALTER TABLE conversation ADD CONSTRAINT conversation_tenant_canal_unico
    UNIQUE (tenant_id, id, channel_connection_id);
ALTER TABLE contact ADD CONSTRAINT contact_tenant_id_unico UNIQUE (tenant_id, id);
ALTER TABLE pipeline ADD CONSTRAINT pipeline_tenant_id_unico UNIQUE (tenant_id, id);
ALTER TABLE pipeline_stage ADD CONSTRAINT pipeline_stage_tenant_id_unico UNIQUE (tenant_id, id);
ALTER TABLE pipeline_stage ADD CONSTRAINT pipeline_stage_tenant_funil_id_unico
    UNIQUE (tenant_id, pipeline_id, id);
ALTER TABLE deal ADD CONSTRAINT deal_tenant_id_unico UNIQUE (tenant_id, id);

ALTER TABLE refresh_token ADD CONSTRAINT refresh_token_usuario_mesmo_tenant
    FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id);

ALTER TABLE contact ADD CONSTRAINT contact_responsavel_mesmo_tenant
    FOREIGN KEY (tenant_id, owner_user_id) REFERENCES app_user (tenant_id, id);

ALTER TABLE conversation ADD CONSTRAINT conversation_canal_mesmo_tenant
    FOREIGN KEY (tenant_id, channel_connection_id)
    REFERENCES channel_connection (tenant_id, id);
ALTER TABLE conversation ADD CONSTRAINT conversation_atendente_mesmo_tenant
    FOREIGN KEY (tenant_id, assigned_user_id) REFERENCES app_user (tenant_id, id);
ALTER TABLE conversation ADD CONSTRAINT conversation_contato_mesmo_tenant
    FOREIGN KEY (tenant_id, contact_id) REFERENCES contact (tenant_id, id);

ALTER TABLE message ADD CONSTRAINT message_conversa_canal_mesmo_tenant
    FOREIGN KEY (tenant_id, conversation_id, channel_connection_id)
    REFERENCES conversation (tenant_id, id, channel_connection_id);
ALTER TABLE message ADD CONSTRAINT message_autor_mesmo_tenant
    FOREIGN KEY (tenant_id, author_user_id) REFERENCES app_user (tenant_id, id);

ALTER TABLE channel_credential ADD CONSTRAINT channel_credential_canal_mesmo_tenant
    FOREIGN KEY (tenant_id, channel_connection_id)
    REFERENCES channel_connection (tenant_id, id);
ALTER TABLE inbound_event ADD CONSTRAINT inbound_event_canal_mesmo_tenant
    FOREIGN KEY (tenant_id, channel_connection_id)
    REFERENCES channel_connection (tenant_id, id);

ALTER TABLE pipeline_stage ADD CONSTRAINT pipeline_stage_funil_mesmo_tenant
    FOREIGN KEY (tenant_id, pipeline_id) REFERENCES pipeline (tenant_id, id);

ALTER TABLE deal ADD CONSTRAINT deal_etapa_do_funil_mesmo_tenant
    FOREIGN KEY (tenant_id, pipeline_id, stage_id)
    REFERENCES pipeline_stage (tenant_id, pipeline_id, id);
ALTER TABLE deal ADD CONSTRAINT deal_contato_mesmo_tenant
    FOREIGN KEY (tenant_id, contact_id) REFERENCES contact (tenant_id, id);
ALTER TABLE deal ADD CONSTRAINT deal_responsavel_mesmo_tenant
    FOREIGN KEY (tenant_id, owner_user_id) REFERENCES app_user (tenant_id, id);

ALTER TABLE task ADD CONSTRAINT task_contato_mesmo_tenant
    FOREIGN KEY (tenant_id, contact_id) REFERENCES contact (tenant_id, id);
ALTER TABLE task ADD CONSTRAINT task_oportunidade_mesmo_tenant
    FOREIGN KEY (tenant_id, deal_id) REFERENCES deal (tenant_id, id);
ALTER TABLE task ADD CONSTRAINT task_responsavel_mesmo_tenant
    FOREIGN KEY (tenant_id, assigned_user_id) REFERENCES app_user (tenant_id, id);
