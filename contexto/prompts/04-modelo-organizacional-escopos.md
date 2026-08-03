---
id: "04"
title: "Modelo organizacional e escopos"
phase: "foundation"
risk: "high"
prerequisites: ["00", "03"]
produces: ["modelo B2B/B2C", "hierarquia de escopo", "contratos públicos"]
gate: "A"
---

# Prompt 04 — Modelo organizacional e escopos

## Objetivo

Formalize o modelo organizacional antes do RBAC, sem reutilizar usuário interno
como cliente final.

## Protocolo obrigatório

Leia código e glossário. Se a mudança exigir migração incompatível ou redefinir
contrato comercial, pare com alternativas e impacto. Defaults reversíveis
preservam o modelo atual. Regras relevantes ficam fora do controller e são
testáveis sem HTTP; não crie entidade rica para CRUD sem invariantes.

## Modelo mínimo

- `Tenant`: organização contratante;
- `Unit`: filial/unidade operacional;
- `InternalUser`: pessoa que trabalha na organização;
- `Contact/Customer`: cliente final B2B ou B2C;
- `Membership`: vínculo do usuário com tenant/unidade e vigência;
- `Role`: conjunto nomeado de permissões;
- `Scope`: rede, tenant, unidade, equipe ou próprio registro.

## Trabalho e aceite

1. Documente cardinalidade, propriedade, ciclo de vida e exclusão de cada tipo.
2. Modele usuário com mais de uma unidade sem duplicar identidade.
3. Separe módulo técnico, entitlement, permissão e visibilidade da navegação.
4. Exponha somente APIs públicas mínimas entre módulos.
5. Teste vínculo expirado, troca de unidade, usuário multiunidade, contato B2B e
   B2C e tentativa de vínculo cruzado entre tenants.
6. Atualize glossário e crie ADR individual para decisões que fechem portas.

Gate A exige diagrama/modelo, migration quando necessária, testes e evidência
com commit, ambiente, data, comandos, resultados e responsável.
