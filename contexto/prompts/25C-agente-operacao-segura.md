---
id: "25C"
title: "Agente privado — atualização e operação segura"
phase: "private_integrations_scale"
risk: "critical"
prerequisites: ["25B", "22"]
produces: ["update assinado", "rotação operacional", "runbooks do agente"]
gate: "F"
---

# Prompt 25C — Agente privado: operação segura

## Objetivo

Complete atualização, revogação, rotação, compatibilidade e observabilidade do
agente depois de protocolo e jobs estarem aprovados.

## Protocolo obrigatório

Binário/update não assinado nunca é instalado. Política de canal, rollback,
janela e suporte de versão é decisão operacional. Agente comprometido deve ser
isolável sem expor segredo ou derrubar outros tenants. Telemetria não contém
payload de job, segredo local ou dados do sistema do cliente.

## Trabalho

1. Verifique assinatura e hash antes de instalar; proteção contra downgrade.
2. Faça rollout por canal/versão com pausa, rollback e compatibilidade declarada.
3. Rotacione credencial sem janela de dupla identidade indefinida.
4. Revogue agente/versão e invalide jobs pendentes conforme política.
5. Meça heartbeat, versão, falhas, fila e latência com cardinalidade controlada.
6. Escreva runbooks de agente offline, comprometido, update ruim e perda do cofre.

## Testes e aceite

- pacote adulterado/desatualizado é rejeitado;
- rollout interrompido recupera versão íntegra conhecida;
- rotação/revogação concorrentes convergem;
- servidor incompatível não envia job impossível;
- Gate F contém trilha de enrollment, job, update e revogação sanitizada.
