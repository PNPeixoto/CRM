---
id: "13"
title: "Adaptadores oficiais de provedor"
phase: "omnichannel"
risk: "high"
prerequisites: ["12"]
produces: ["adapter normalizado", "contract tests", "reconciliação de estado"]
gate: "D"
---

# Prompt 13 — Adaptadores de provedor

## Objetivo

Implemente um provedor por PR sobre a fundação normalizada, usando API oficial
em produção e documentação oficial vigente.

## Protocolo obrigatório

Não confie em documentação memorizada: consulte fonte oficial, fixe versão em
configuração e versione fixtures sem dados reais. Bridge não oficial de
WhatsApp não é permitida em produção sem decisão jurídica/comercial explícita.
Credenciais só aparecem pelo nome da variável e ficam cifradas/referenciadas.

## Trabalho

1. Traduza payload oficial para o contrato normalizado e preserve específico
   apenas em payload restrito de diagnóstico.
2. Valide assinatura nos bytes crus em tempo constante e imponha tamanho.
3. Trate `429`/`Retry-After`, timeouts, rate limit por conexão/tenant e falhas
   externas. Circuit breaker só com padrão real medido.
4. Reconcilie status de envio/leitura e estado remoto; não confie só em evento.
5. Para mídia: host oficial/autenticado, streaming, limite antes/durante,
   magic bytes/parser, nome gerado, quarentena, storage fora do web root, URL
   assinada curta e `Content-Disposition` seguro. Bloqueie HTML/SVG executável.
6. Nunca use link temporário do provedor como única cópia.

## Testes e aceite

- contract tests com payloads oficiais versionados;
- assinatura inválida, replay, 429, timeout e resposta malformada cobertos;
- nenhum segredo/resposta externa crua persiste em log;
- reconciliação corrige lacuna de webhook;
- mídia respeita isolamento, tamanho, tipo e retenção.
