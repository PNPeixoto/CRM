# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: (preencher)

## Onde parei

Estrutura do repositório criada. Nenhuma funcionalidade implementada.

## Pronto

- Esqueleto do backend (Spring Boot + Modulith) com pacotes dos módulos
- Esqueleto do frontend (Vite + React + TS + Tailwind)
- Registro central de rotas com status por página
- Todas as páginas com pasta e arquivo TSX criados, marcadas "em produção"

## Em andamento

Nada.

## Próximo passo

Autenticação: entidade de usuário, migration inicial, endpoint de login,
tela de login consumindo a API.

## Bloqueios e pendências

- Definir modo claro ou escuro como padrão antes de gerar os design tokens
- Definir nome definitivo do produto (`pnp` é provisório e já está no
  nome do pacote Java)
- Decidir API oficial do WhatsApp vs. bridge não oficial

## Armadilhas conhecidas

- Broker STOMP em memória não funciona com mais de uma instância
- `ddl-auto` deve permanecer em `validate`
