# Glossário do domínio

| Termo | Significado |
|---|---|
| Tenant | Organização contratante e raiz de isolamento do SaaS; não é unidade nem cliente final |
| Unidade | Filial ou operação pertencente a exatamente um tenant; não duplica usuários internos |
| Usuário interno | Identidade tenant-scoped da pessoa que trabalha na organização; nunca representa cliente final |
| Contato/cliente | Parte atendida pelo tenant: pessoa B2C (`PERSON`) ou organização B2B (`ORGANIZATION`) |
| Membership | Vínculo único e temporal entre usuário interno e tenant; os escopos ligam esse vínculo a uma ou mais unidades |
| Role | Conjunto tenant-scoped e nomeado de permissões; não concede alcance sozinho |
| Permissão | Ação estável do servidor, como `contacts.read`; sempre avaliada junto de um scope |
| Scope | Alcance da autorização: rede, tenant, unidade, equipe ou próprio registro |
| Capability | Recurso técnico publicado pelo build/backend; existir no código não concede contratação nem acesso |
| Entitlement | Capacidade contratada pelo tenant; não equivale a permissão do usuário |
| Visibilidade de navegação | Decisão de apresentação derivada de capability, entitlement, permissão e preset; nunca é barreira de segurança |
| Conversa | Thread de mensagens com um contato, em um canal |
| Conexão de canal | Um número ou conta específica ligada a uma unidade |
| Oportunidade | Negócio em andamento dentro de um funil |
| Janela de 24h | Período em que o WhatsApp permite resposta em texto livre |
| Agente privado | Componente instalado no cliente que guarda credenciais locais |
