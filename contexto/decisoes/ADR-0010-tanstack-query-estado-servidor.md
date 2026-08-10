# ADR-0010 — TanStack Query atrás da fronteira de estado do servidor

- Status: accepted
- Data: 2026-08-08

## Contexto

O frontend mantinha respostas HTTP em `useState`/`useEffect` em contatos,
tarefas, funis, canais, dashboard, relatórios e inbox. A mesma visão geral era
buscada separadamente por duas páginas; buscas e trocas de conversa não
cancelavam a resposta anterior; cada mutação inventava sua própria recarga.

O F5 exige isolamento por tenant, unidade e identidade, cancelamento de
corridas, retenção explícita, invalidação seletiva e rollback verificável.
Implementar isso em utilitário próprio significaria criar um cache concorrente
sem necessidade de domínio que justificasse mantê-lo.

## Decisão

Adotar `@tanstack/react-query` 5.101.4, com versão exata, somente dentro de
`shared/server-state`. Páginas importam hooks de `recursos.ts`, não a biblioteca.

Toda chave tem a forma:

`servidor / tenant / unidade-ou-tenant-inteiro / identidade / recurso / parâmetros`

A fronteira cria um `QueryClient` por identidade/contexto. Logout, saída em
outra aba e adoção de nova sessão cancelam requisições e limpam os clientes
antes de atualizar o usuário visível. Não há persister, IndexedDB, storage ou
cache offline.

Defaults: dado fresco por 30 segundos, retenção inativa por 5 minutos, refetch
ao reconectar, sem refetch automático por foco e sem retry adicional — o
cliente HTTP já repete leituras transitórias com backoff. Recursos podem reduzir
ou ampliar `staleTime` quando sua volatilidade é conhecida.

Atualização otimista fica restrita à conclusão reversível de tarefa. Ela salva
todas as listas afetadas, cancela leituras, aplica a projeção, restaura o
snapshot no erro, reconcilia com a resposta e invalida tarefas/visão geral.

## Ganho mensurável

- sete superfícies deixam de manter uma cópia manual do estado remoto;
- dashboard e relatórios compartilham uma consulta;
- troca de chave cancela/segrega respostas fora de ordem;
- seis cenários de risco têm prova dedicada: chave, contexto, corrida, logout,
  rollback e invalidação seletiva;
- invalidação passa a ser por recurso, sem recarga global.

O custo é duas dependências de produção e aumento do bundle principal. A
fronteira própria contém esse custo e evita espalhar API de fornecedor pelas
telas.

## Alternativas descartadas

- **Manter efeitos locais:** não resolve deduplicação, corrida, isolamento nem
  rollback sem repetir lógica em cada página.
- **Cache próprio:** reduziria a dependência externa, mas recriaria lifecycle,
  cancelamento, garbage collection, observadores e concorrência.
- **Cache persistente/offline:** amplia exposição de dados e exige política de
  cifragem, expiração e conflito fora do escopo do F5.

## Remoção e revisão

A biblioteca pode ser removida substituindo a implementação de
`shared/server-state`; páginas e modelos de domínio permanecem estáveis. Rever
se o custo de bundle superar o ganho medido, se surgir requisito offline real
ou se o backend publicar contexto de unidade aplicável aos agregados.

## Evidências

`frontend/src/shared/server-state/`,
`frontend/src/shared/server-state/estadoServidor.test.tsx` e
`frontend/ESTADO-SERVIDOR.md`.
