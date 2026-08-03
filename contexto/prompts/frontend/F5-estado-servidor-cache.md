---
id: "F5"
canonical_id: "frontend:F5"
title: "Estado de servidor, cache e invalidação"
phase: "frontend_foundation"
risk: "high"
prerequisites: ["frontend:F3", "frontend:F4"]
blocking: "before-external-pilot"
produces: ["política de cache", "chaves segregadas", "invalidação verificável"]
gate: "C"
---

# F5 — Estado de servidor e cache

## Objetivo

Separe estado remoto de estado de interface e impeça vazamento ou exibição de
dados antigos entre usuários, tenants e unidades.

## Trabalho

1. Inventarie estado remoto, local, de formulário e de URL. Não replique resposta
   do servidor em store global sem necessidade comprovada.
2. Preserve a solução atual se suficiente; adote biblioteca madura somente por
   ADR com ganho mensurável, custo de migração e estratégia de remoção.
3. Inclua tenant, unidade, identidade e parâmetros relevantes em toda chave de
   cache. Limpe antes de trocar contexto ou encerrar sessão.
4. Defina `staleTime`, retenção, refetch e invalidação por recurso/operação. Não
   use invalidação global como padrão.
5. Use atualização otimista apenas quando reversível e com reconciliação. Ações
   financeiras ou irreversíveis aguardam confirmação do servidor.
6. Exiba dado possivelmente antigo somente em condição material: offline, limiar
   excedido, domínio crítico ou falha de atualização. Refetch normal é silencioso.
7. Não crie cache offline persistente neste prompt.
8. Teste isolamento de chaves, troca de contexto, logout, corrida de respostas,
   rollback e invalidação seletiva.

## Aceite

- não há instante em que dados do contexto anterior apareçam com o novo rótulo;
- chaves documentam todas as dimensões de autorização;
- mutação otimista tem rollback e reconciliação determinísticos;
- tela não multiplica banners de atualização normal;
- cache persistente sensível continua inexistente.

