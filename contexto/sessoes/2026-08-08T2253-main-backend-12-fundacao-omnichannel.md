# Sessao 2026-08-08 — backend:12 fundacao omnichannel

- Branch observada: `main`
- Ambiente: Windows, JDK 25, Docker disponivel
- Responsavel: Codex

## Entregue

- V15 transforma as filas de entrada e saida em reservas com lease, recuperacao
  apos abandono e dead letter explicita;
- funcoes tenant-scoped permitem reprocessar entrada e saida sem UPDATE manual;
- o webhook recebe bytes, calcula SHA-256 e converge replays concorrentes mesmo
  quando o id externo nao pode ser extraido;
- o corpo bruto e cifrado com AES-256-GCM antes da persistencia, com prazo de
  retencao e expurgo que preserva metadados de idempotencia;
- `usage_event` mede entrada aceita, saida entregue e bytes armazenados, usa RLS,
  chave idempotente e bloqueia UPDATE/DELETE;
- workers nao registram payload, token ou segredo e limpam o lease ao concluir;
- V15 preserva a assinatura das funcoes antigas para permitir deploy gradual de
  banco e aplicacao.

## Evidencias

- concorrencia real de replay converge para uma unica linha;
- banco vazio e atualizacao desde V8 chegam a V17;
- RLS, privilegios minimos, imutabilidade de medicao, lease, dead letter,
  reprocessamento e retencao possuem testes de integracao;
- suite completa compartilhada com o Prompt 13: 149 testes backend.

## Decisoes e rollback

O hash nao substitui a cifra: ele existe para idempotencia, enquanto o
ciphertext temporario permite processar o evento sem persistir texto em claro.
O expurgo e security-definer porque atravessa tenants, mas seu contrato e
estreito, limita o lote e nao concede acesso ao payload.

As migrations sao aditivas. O binario anterior continua usando as assinaturas
de reserva substituidas pela V15. O rollback e do binario; remocao de colunas ou
dados exige migration compensatoria revisada.

## Gate

O Prompt 12 esta concluido. O Gate D permanece aberto ate o Prompt 16.

