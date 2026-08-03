---
id: "F8"
canonical_id: "frontend:F8"
title: "Tempo real resiliente"
phase: "frontend_product"
risk: "high"
prerequisites: ["backend:14", "frontend:F4", "frontend:F5"]
blocking: "feature-activation:omnichannel"
produces: ["cliente realtime", "reconciliação por cursor", "fallback adaptativo"]
gate: "D"
---

# F8 — Tempo real

## Objetivo

Consuma atualizações em tempo real sem tornar o socket fonte de verdade e sem
perder, duplicar ou reordenar silenciosamente eventos.

## Trabalho

1. Mantenha REST como fonte de verdade. Socket sinaliza mudanças e carrega
   identificadores/versões suficientes para reconciliar.
2. Implemente ciclo autenticado com backoff exponencial e jitter. Em logout pare;
   após expiração, reautentique antes de assinar novamente.
3. Enquanto desconectado, use polling adaptativo: reduza em background, pause
   offline e retome com cursor. Mostre última sincronização apenas quando útil.
4. Persista cursor somente na memória apropriada por stream/recurso. Ao detectar
   lacuna, recupere pelo contrato do backend antes de avançar.
5. Deduplicate por ID e respeite sequência/versão do servidor. Mensagem própria
   pode ser otimista somente com ID idempotente e reconciliação explícita.
6. Atualize contagens sem roubar foco. `aria-live` anuncia apenas atividade
   relevante ao usuário ativo, em lotes e sem conteúdo sensível em background.
7. Teste reconexão, gap, duplicata, fora de ordem, troca de contexto, expiração,
   offline/background, polling e mensagem própria.

## Aceite

- reconectar não cria assinatura duplicada nem tempestade de requisições;
- lacuna nunca é tratada como sucesso silencioso;
- socket e polling convergem ao mesmo estado REST;
- atualização não move foco nem anuncia conteúdo privado indevidamente;
- o cliente respeita cursor, sequência e política do backend:14.

