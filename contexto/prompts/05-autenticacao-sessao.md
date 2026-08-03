---
id: "05"
title: "Autenticação e sessão"
phase: "core_security"
risk: "high"
prerequisites: ["03", "04"]
produces: ["login seguro", "recuperação e MFA", "sessões revogáveis"]
gate: "B"
---

# Prompt 05 — Autenticação e sessão

## Objetivo

Entregue autenticação resistente a enumeração, credential stuffing e roubo de
sessão, mantendo possibilidade futura de passkeys sem implementá-las agora.

## Protocolo obrigatório

Não invente segredo nem provedor. Verifique recomendações vigentes do NIST em
fonte oficial antes de fixar política. Escolha de federação, MFA obrigatório ou
contrato de identidade é decisão de segurança: pare se não estiver aprovada.
Token nunca vai para `localStorage`, log, evidência ou documento.

## Trabalho

1. Argon2id com salt automático, pepper fora do banco e benchmark no ambiente.
2. Login uniforme em corpo, status e custo; normalize também as chaves de rate
   limit. Evite bloqueio que permita DoS deliberado contra a conta.
3. Access token curto; refresh aleatório, armazenado por hash, rotativo e com
   detecção de reutilização/família.
4. Cookie `Secure`, `HttpOnly`, `SameSite` e escopo mínimo em produção.
5. Recuperação usa token único, curto, armazenado por hash e invalida sessões
   conforme política.
6. MFA para proprietário, administrador e ações sensíveis; códigos de recuperação
   protegidos e uso auditado.
7. Revogação de todas as sessões e caminho para credencial comprometida.

## Testes e aceite

- tenant/login/senha inexistentes são indistinguíveis;
- reuso e concorrência de refresh são exercitados no banco real;
- CSRF, cookie, logout, reset, MFA e rate limit têm testes de abuso;
- segredos e tokens não aparecem em respostas ou logs;
- matriz do Gate B aponta implementação e evidência.
