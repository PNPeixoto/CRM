# CRM PNP

Ponto de entrada de contexto. Leia na ordem antes de qualquer tarefa.

## Ordem de leitura obrigatória

1. `contexto/00-projeto.md` — o que é o produto, módulos, prioridades
2. `contexto/01-padroes-tecnicos.md` — código, segurança, infra, canais
3. `contexto/02-estado-atual.md` — **onde parei e qual é o próximo passo**

Consulte sob demanda, não por padrão:

- `contexto/03-decisoes.md` — histórico de decisões (append-only)
- `contexto/04-glossario.md` — termos do domínio
- `contexto/05-reaproveitamento-finup.md` — o que aproveitar do projeto
  anterior (FinUp), item a item, com veredito e motivo
- `contexto/sessoes/` — log detalhado por data
- `contexto/PROMPT-PROXIMA-SESSAO.md` — plano de trabalho corrente, com
  faseamento e critérios de aceite. Substituído a cada sessão, não é
  histórico.

## Regras de sessão

**No início:** ler os três arquivos obrigatórios. Se `02-estado-atual.md`
contradisser o código, o código vence — e corrija o arquivo.

**Durante:** ao tomar uma decisão técnica que fecha uma porta (escolha de
biblioteca, formato de dado, desenho de tabela), registrar em
`03-decisoes.md` **no momento da decisão**, não no fim.

**No fim:** reescrever `02-estado-atual.md` por inteiro e criar
`contexto/sessoes/AAAA-MM-DD.md` com o que foi feito.

## Limites

- `02-estado-atual.md` não passa de 150 linhas. Se passar, é porque virou
  histórico — mova para `03-decisoes.md` ou para o log da sessão.
- `03-decisoes.md` é **append-only**. Nunca editar entrada antiga. Decisão
  revertida vira entrada nova que referencia a anterior.
- Nunca gravar segredo, token ou credencial em nenhum arquivo de contexto.
