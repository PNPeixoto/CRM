# Sessão 2026-08-14 — ficha 360º e auditoria de apresentação

- Branch: `agent/refino-apresentacao`
- Head anterior: `0b78d22`
- Migrations: nenhuma
- Frontend: **147 testes em 33 arquivos**, build, lint e `api:check` verdes
- Backend: teste anti-IDOR adicionado; execução pendente por falta de JDK 25

## Entrega

`/contatos` ganhou uma ficha de aprofundamento acionada pelo ícone de
visualização. Ela preserva busca e página na URL, carrega o contato pontual e
mostra cadastro, responsável, oportunidades, valor em aberto e atividades. A
conclusão/reabertura usa a mesma mutação otimista da Agenda e de Tarefas.

A visão foi separada em `ContactDetails.tsx`; a listagem não é consultada
enquanto a ficha está aberta. O identificador técnico é o único dado novo na
URL e é codificado antes de compor caminhos HTTP. E-mail, telefone e observação
são renderizados como texto pelo React; os links usam prefixos fixos `mailto:`
e `tel:`.

O cenário local liga Maria Silva ao Grupo Horizonte, à oportunidade de
R$ 128.000 e a três atividades. `frontend/demo/server.mjs` implementa contato
por ID, oportunidades por contato e filtro de tarefas, preservando o estado ao
concluir/reabrir.

## Contrato e autorização

- `GET /api/contatos/{contatoId}/oportunidades` valida o contato pelo módulo
  público, exige `contacts.read` sobre o registro e aplica o recorte de
  `deals.read` dentro da consulta.
- `GET /api/tarefas?contatoId=...` faz a mesma validação de contato e combina o
  filtro com o recorte de `tasks.read` dentro da consulta.
- Os índices `deal_do_contato` e `task_do_contato` já existem desde a V5; não
  houve migration ou consulta por varredura completa.
- `AutorizacaoPorAlcanceTest` cobre retorno filtrado e recusa `403` ao tentar
  usar a ficha para consultar contato de outra carteira.

Conversas ficaram deliberadamente fora da associação. A coluna
`conversation.contact_id` existe, mas a entidade e o fluxo de identificação
ainda não a populam nem publicam. O botão abre a Inbox sem afirmar que existe
uma conversa vinculada.

## Auditoria de código

- Tipos são gerados pelo OpenAPI e mapeados na fronteira HTTP; páginas não
  interpretam DTO de transporte.
- Consulta da lista é desabilitada na ficha, evitando download redundante.
- A data civil de previsão deixou de passar por fuso horário; `2026-08-31`
  permanece `31/08/2026` e ganhou regressão unitária.
- Estados de carregamento, erro, vazio, permissão e mutação estão separados.
- A ficha foi extraída da página de listagem após a revisão de tamanho e
  responsabilidade do módulo.

## Auditoria de segurança

- O contrato de segurança do navegador passou: sem HTML arbitrário, `eval`,
  esquema executável, persistência de token ou source map de produção.
- `npm audit --omit=dev`: zero vulnerabilidades no runtime.
- A árvore completa mantém três alertas altos do mesmo advisory
  `GHSA-5p4m-2wfm-xmqj`, somente no gerador OpenAPI. A exceção já documentada
  está vigente até 2026-11-30; o verificador oficial da CI passou.
- Gitleaks não existe no host. A CI continua fixando a action por SHA e varre o
  histórico completo; nenhum segredo foi introduzido no diff revisado.

## Auditoria de desempenho

- Chunk de Contatos: 16,15 kB bruto e **5,25 kB gzip**; shell principal:
  371,89 kB bruto e 117,02 kB gzip. Não há imagem ou source map novo.
- A ficha faz três consultas de domínio com quantidade fixa: contato,
  oportunidades filtradas e tarefas filtradas; não há requisição por funil ou
  por item.
- Em Vite local, sem throttling, viewport 1280×720, cinco recargas ficaram em
  564–617 ms até o título da ficha, mediana 585 ms e amplitude 53 ms.
- Em 390×844, `scrollWidth`, `clientWidth` e viewport ficaram em 390 px; sem
  overflow horizontal. Isso é amostra local, não baseline de produção nem Web
  Vitals; portanto nenhum budget de promoção foi inventado.

## Verificações e limites

- `npm run test:quick` — 33 arquivos, 147 testes;
- `npm run build`, `npm run lint`, `npm run api:check`, `git diff --check`;
- teste real desktop e mobile da lista, ficha e conclusão/reabertura;
- `npm audit --omit=dev` e verificador oficial de exceções da CI.

O Maven Wrapper foi exercitado com o cache liberado, mas o host usa Java 21 e
falhou com `release version 25 not supported`. A tentativa de sobrescrever a
release para 21 também falhou em dependência compilada para runtime mais novo.
Logo, o teste backend e o snapshot OpenAPI real precisam rodar primeiro na CI
Java 25. O risco funcional remanescente é a ausência de paginação nas relações
da ficha para históricos extremos.
