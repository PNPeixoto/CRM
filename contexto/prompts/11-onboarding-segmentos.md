---
id: "11"
title: "Onboarding e apresentação por segmento"
phase: "usable_product"
risk: "medium"
prerequisites: ["06", "09", "10"]
produces: ["perfil inicial", "preset versionado", "navegação coerente"]
gate: "C"
---

# Prompt 11 — Onboarding e segmentos

## Objetivo

Entregue onboarding que escolhe um preset inicial de apresentação, sem criar
verticais, autorização por menu ou acoplamento dos módulos ao segmento.

## Protocolo obrigatório

O segmento é default mutável pelo usuário, não fronteira de produto. Tenant vem
do contexto autenticado. Módulo disponível, entitlement, permissão e navegação
são separados. Preserve funis/dados já personalizados; migração destrutiva ou
reaplicação automática de preset exige decisão.

## Trabalho

1. Persista perfil/preset/versionamento e conclusão do onboarding com RLS.
2. Exponha contrato público imutável; módulos não importam entidade interna.
3. Crie defaults de navegação/rótulo/funil de modo idempotente e atômico.
4. Resolva menu e roteador por IDs estáveis a partir da mesma função.
5. Bloqueie no backend ações que dependam do onboarding, não apenas na UI.
6. Garanta recarga imediata após escolha, sem novo login.

## Testes e aceite

- tenants com segmentos distintos não vazam perfil ou referência;
- duas conclusões concorrentes convergem sem duplicar funil;
- preset novo não reescreve tenant já configurado;
- rota oculta não concede/revoga permissão;
- onboarding acessível, build e Gate C verdes.
