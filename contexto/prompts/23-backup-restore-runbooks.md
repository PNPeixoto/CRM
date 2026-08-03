---
id: "23"
title: "Backup, restore e runbooks"
phase: "operations_compliance"
risk: "critical"
prerequisites: ["03", "18", "22"]
produces: ["backup protegido", "restore ensaiado", "RPO/RTO medidos"]
gate: "E"
---

# Prompt 23 — Backup, restore e runbooks

## Objetivo

Prove recuperação real de banco e objetos. Backup não restaurado não é evidência.

## Protocolo obrigatório

RPO/RTO são compromisso operacional/contratual: use os alvos já aprovados ou
pare para decisão. Não execute restore sobre produção. Chave de backup fica
separada; evidência nunca contém dump, segredo ou amostra de cliente.

## Trabalho

1. Combine backup lógico e PITR/WAL conforme RPO, incluindo mídia/anexos.
2. Cifre antes de sair do host, use cópia fora do provedor, versionamento e
   proteção contra exclusão/ransomware.
3. Defina retenção alinhada ao prompt 18 e legal hold.
4. Automatize restauração em ambiente descartável e valide integridade/contagem.
5. Faça backup falhar ruidosamente por ausência, tamanho anômalo ou restore ruim.
6. Escreva runbooks de perda de banco, objeto, credencial, região e migration.

## Testes e aceite

- restore completo e seletivo executados com tempos medidos;
- RPO/RTO observados e comparados ao alvo;
- checksum/integridade e referências banco↔storage validados;
- credencial comprometida tem rotação e recuperação ensaiadas;
- rollback de migration distingue expand/contract de operação destrutiva;
- relatório sanitizado integra Gate E.
