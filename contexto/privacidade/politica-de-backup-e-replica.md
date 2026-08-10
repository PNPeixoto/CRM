# Backup, réplica e o limite do expurgo

Fecha o `LGPD-004`. O aceite do Prompt 18 exige que backup e réplicas tenham
tratamento documentado, e a razão é incômoda: **expurgo que não alcança backup
não é expurgo**.

## O problema, dito sem rodeio

Quando o sistema anonimiza um contato ou apaga uma conversa, o dado sai do
banco em produção. Ele **não sai dos backups já tirados**. Um backup de trinta
dias atrás continua contendo o nome, o e-mail e as mensagens de alguém que
exerceu o direito de eliminação ontem.

Isso não é defeito de implementação — é propriedade de qualquer backup. Fingir
o contrário seria pior que admitir.

## O que já é verdade hoje

| Fato | Situação |
|---|---|
| Backup automatizado | **Não existe.** Nenhuma rotina agendada, nenhum destino configurado |
| Backups avulsos | Dois, criados manualmente nesta sequência de trabalho |
| Réplica de leitura | Não existe |
| Retenção de backup | Não definida, porque não há rotina |
| Cifra em repouso do backup | Não definida |

Os dois backups avulsos são:

1. `crm-antes-v22-*.dump` — banco inteiro, tirado antes de aplicar a V22.
2. `event-publication-antes-expurgo.dump` — a tabela de eventos, tirada antes
   de expurgar as publicações concluídas do `LGPD-001`.

Ambos estão num diretório temporário de sessão, **fora do repositório**, e
**contêm dado pessoal**: contatos, conversas e — no segundo — texto de mensagem
de cliente em claro, que é exatamente o que o `LGPD-001` removeu do banco.

Isso é um exemplo vivo do problema: o dado foi eliminado da produção e
sobrevive no backup tirado minutos antes.

## Regra vigente, enquanto não houver rotina

**Backup manual é temporário por definição.** Quem o cria assume três deveres:

1. Guardá-lo fora do repositório. Nenhum `.dump` entra em git.
2. Apagá-lo assim que a operação que o motivou for considerada estável.
3. Não usá-lo como arquivo histórico. Backup é para restaurar uma falha
   recente, não para consultar o passado.

Os dois backups desta sequência devem ser apagados quando a V22 e o expurgo do
`LGPD-001` estiverem consolidados.

## O que precisa existir antes de qualquer cliente real

Nada abaixo está implementado. A lista é o contrato que o Prompt 23
(backup/restore/runbooks) precisa cumprir, e é aqui porque o Prompt 18 exige
que o tratamento esteja **documentado**, mesmo quando a resposta é "ainda não
temos".

- **Prazo de retenção do backup**, curto e declarado. Ele define a janela em
  que um dado eliminado ainda existe em algum lugar — e essa janela precisa
  ser dizível ao titular.
- **Cifra em repouso**, com chave separada das chaves da aplicação.
- **Restauração ensaiada**, com RPO e RTO medidos. Backup nunca restaurado é
  hipótese, não garantia.
- **Procedimento pós-restauração**: reaplicar as eliminações que ocorreram
  entre o backup e o incidente. Sem isso, restaurar ressuscita dado que o
  titular mandou apagar — e o sistema volta a violar o direito que já havia
  atendido.
- **Registro de acesso ao backup.** Quem restaura enxerga tudo, inclusive o que
  a autorização por registro esconde no dia a dia.

## Como responder ao titular hoje

Se alguém exercer o direito de eliminação, a resposta honesta é:

> O dado foi removido dos sistemas em produção. Cópias de segurança
> eventualmente existentes são temporárias, não são consultadas na operação, e
> a remoção é reaplicada caso alguma precise ser restaurada.

A segunda frase **só será verdadeira** quando o procedimento pós-restauração
existir. Enquanto não existir, quem responder precisa saber que está
descrevendo uma intenção, e não um controle.

## Relação com o resto

- O expurgo em produção está no `LGPD-002`, com legal hold e relógio
  controlado.
- Os direitos do titular estão no `LGPD-003`.
- A execução deste documento pertence ao Prompt 23.
