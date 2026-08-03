# CRM PNP

Ponto de entrada canônico para agentes e colaboradores.

## Leitura obrigatória

1. `contexto/00-projeto.md` — produto, módulos e prioridades;
2. `contexto/01-padroes-tecnicos.md` — construção, segurança e operação;
3. `contexto/02-estado-atual.md` — estado consolidado e próximo passo.

O código e a configuração executável vencem documento desatualizado. Relate a
divergência; atualize o estado consolidado somente na integração à `main` ou em
rotina explicitamente encarregada de consolidá-lo.

## Roteiro canônico

A trilha principal/backend vigente é `contexto/prompts/manifest.yaml`, versão 3;
a trilha complementar de interface é
`contexto/prompts/frontend/manifest.yaml`, versão 4. Elas usam os namespaces
`backend:` e `frontend:` e declaram dependências cruzadas. Juntas substituem os
pacotes anteriores e `contexto/PROMPT-PROXIMA-SESSAO.md`. Confira ordem,
pré-requisitos, bloqueio e gates antes de iniciar uma fase. Cada execução deve
caber em uma branch/PR revisável.

Consulte sob demanda:

- `contexto/decisoes/` — ADRs individuais vigentes;
- `contexto/03-decisoes.md` — arquivo histórico legado, somente leitura;
- `contexto/04-glossario.md` — termos do domínio;
- `contexto/05-reaproveitamento-finup.md` — decisões sobre o projeto anterior;
- `contexto/sessoes/` — logs de execução, sem material sensível.

## Protocolo de decisão

Pare e peça decisão somente quando a escolha:

1. for irreversível ou cara de desfazer;
2. envolver segurança, privacidade, contrato ou cobrança;
3. contrariar ADR aceito;
4. depender de informação inexistente no repositório.

Em escolha reversível, preserve o comportamento atual, use o default menos
invasivo, registre a suposição e não invente preferência de produto.

## Contexto sem conflito entre branches

- Decisão nova recebe arquivo `contexto/decisoes/ADR-NNNN-slug.md`.
- Não edite uma decisão aceita; reversão recebe novo ADR que referencia o antigo.
- Sessão recebe `contexto/sessoes/AAAA-MM-DDTHHMM-branch-tarefa.md`.
- `contexto/02-estado-atual.md` e status do manifesto são consolidados na `main`.
- `contexto/03-decisoes.md` e logs antigos permanecem como histórico legado.

## Segurança documental

Nunca grave senha, token, cookie, segredo, payload de cliente, corpo de webhook,
conteúdo de mensagem, arquivo ou dado pessoal em contexto, ADR, evidência ou log
de sessão. Use nomes de variáveis, identificadores e valores sanitizados.

## Evidência e qualidade

Evidência registra commit, ambiente, data, comando, resultado, artefato e
responsável. Falha de teste não é ignorada. Quarentena exige issue, responsável,
justificativa, expiração e execução contínua; teste crítico de segurança,
tenant, migration ou cobrança não pode ser quarentenado.
