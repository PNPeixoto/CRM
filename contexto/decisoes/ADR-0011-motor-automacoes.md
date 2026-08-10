# ADR-0011 — Semântica do motor interno de automações

- Status: accepted
- Data: 2026-08-09

## Contexto

O Prompt 15 introduz execução assíncrona de automações com efeitos internos e
externos. Repetição, alteração de definição, timeout e cancelamento podem gerar
efeitos duplicados ou impossíveis de desfazer. A trilha precisa explicar o que
ocorreu sem armazenar conteúdo de cliente ou segredo.

## Decisão

Cada publicação cria uma versão imutável da definição e um hash SHA-256 do
snapshot validado. Toda execução aponta para essa versão; editar a definição
cria outra versão e nunca altera execuções antigas.

Ações usam identificadores versionados (`*_Vn`) e declaram natureza do efeito e
se o retry é seguro. O worker repete somente ações declaradas seguras, dentro do
limite de tentativas e do deadline original. Pausa não estende o deadline.

Dry-run cria uma execução explicável, valida limites e percorre o plano, mas
não invoca handlers externos. Gatilhos convergem por chave idempotente e uma
cadeia causal não pode disparar novamente a mesma automação.

Cancelamento, timeout e falha são estados terminais. Efeitos externos já
concluídos ou em voo geram um registro de compensação: `REGISTERED` quando
reversível e `IMPOSSIBLE` quando irreversível. O motor não executa compensação
automaticamente nesta etapa; isso exige contrato próprio por ação.

Definições, execuções, passos, compensações e transições são tenant-scoped e
protegidos por RLS forçado. A reserva global retorna somente IDs e o
processamento reentra no contexto do tenant. Transições guardam apenas estado,
código sanitizado, ator, versão e horário — nunca payload, mensagem ou segredo.

## Consequências

- Replay e concorrência convergem sem duplicar a execução lógica.
- Uma execução continua reproduzível após novas versões da definição.
- Efeito em voo tem resultado conservador e visível, sem alegar reversão.
- Pausas longas podem terminar em timeout; operadores devem retomar ou cancelar
  dentro do deadline original.
- Novos conectores só entram no motor com ação versionada e declaração explícita
  de efeito/retry.

## Alternativas descartadas

- Atualizar versão publicada em lugar: quebraria reprodução e auditoria.
- Repetir qualquer falha transitória: poderia duplicar efeito externo.
- Executar handler no dry-run: transformaria simulação em mutação real.
- Guardar payload completo na trilha: ampliaria exposição de dados e segredos.
- Compensar automaticamente sem contrato da ação: poderia produzir novo dano.

## Evidências

`backend/src/main/resources/db/migration/V20__motor_de_automacoes.sql`,
`backend/src/main/java/br/com/pnp/crm/automation/` e
`backend/src/test/java/br/com/pnp/crm/automation/internal/`.
