# Trilha frontend v4

Pacote canônico complementar ao roteiro principal do CRM PNP. Ele substitui a
trilha frontend v3 recebida e incorpora a revisão técnica correspondente.

## Como executar

1. Leia `../manifest.yaml`, `manifest.yaml` e `PREAMBULO.md`.
2. Use o `canonical_id` para dependências e evidências.
3. Execute um prompt por branch/PR revisável; dependências backend e frontend
   precisam estar concluídas ou ter evidência equivalente aprovada.
4. Respeite `blocking`: ele indica quando o risco materializa, não uma ordem
   artificialmente serial entre os dois pacotes.
5. F7 é template repetível. Cada tela usa um identificador de execução como
   `frontend:F7:contacts` e um log de sessão próprio; nunca agrupe telas
   independentes para “concluir o F7”.

## Estados

Os estados são os mesmos do manifesto principal: `ready`, `in_progress`,
`blocked`, `completed` e `superseded`. Alteração de status pertence à
integração/consolidação.

## Responsabilidades

- F0–F6: fundação do cliente;
- F7–F9: entrega incremental de produto;
- F10–F13: auditorias e operação antes de produção;
- testes, acessibilidade, segurança e desempenho são critérios contínuos em
  todos os prompts, não fases adiadas.

O backend continua autoridade de contrato, autenticação, autorização,
entitlements, limites, tenant/unidade e regras monetárias. A trilha frontend é
dona da camada de acesso, apresentação, comportamento no navegador e evidência
de usabilidade.
