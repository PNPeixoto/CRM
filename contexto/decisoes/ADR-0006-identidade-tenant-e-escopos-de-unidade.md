# ADR-0006 — Identidade tenant-scoped e escopos de unidade

- Status: accepted
- Data: 2026-08-01

## Contexto

O CRM precisa representar a mesma pessoa trabalhando em várias unidades sem
duplicar login, sessões ou auditoria. Ao mesmo tempo, ainda não existe requisito
aprovado de uma identidade humana única atravessando empresas contratantes.

## Decisão

`app_user` permanece uma identidade interna tenant-scoped. Existe no máximo uma
membership vigente por usuário/tenant, e múltiplas unidades são atribuídas por
`membership_scope`. Papel agrupa permissões; escopo define alcance e nunca é
inferido só pelo nome do papel.

Escopos de tenant, unidade e próprio registro são persistidos agora. Rede exige
identidade de plataforma e equipe exige um agregado com FK; não serão simulados
por referência polimórfica. Contato continua separado de usuário interno e passa
a declarar pessoa B2C ou organização B2B.

## Alternativas descartadas

- Duplicar `app_user` por unidade: fragmenta senha, MFA, sessão e auditoria da
  mesma pessoa.
- Identidade global entre tenants agora: fecha prematuramente regras de SSO,
  privacidade e desligamento entre empresas sem caso aprovado.
- Colocar `role` diretamente no usuário: mistura ação com alcance e impede
  papéis diferentes por unidade.
- `scope_reference_id` polimórfico: não oferece FK confiável para unidade,
  equipe e rede.

## Consequências e revisão

Trocar unidade não troca identidade nem tenant; apenas ativa um contexto que a
membership já autoriza. Membership ou atribuição expirada falha fechada. Uma
futura força de trabalho entre tenants, SSO corporativo compartilhado, equipes
reais ou operadores de rede dispara revisão por novo ADR e migration aditiva.

## Evidências

V10, `OrganizationAccess`, `ModeloOrganizacionalTest` e
`contexto/modelo-organizacional.md`.
