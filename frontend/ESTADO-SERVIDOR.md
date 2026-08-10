# Estado de servidor e cache

Produto do Prompt `frontend:F5`. A fonte da verdade continua sendo o backend;
o cache apenas coordena leitura, concorrência e atualização em memória.

## Inventário de estado

| Categoria | Exemplos | Local correto |
|---|---|---|
| servidor | apresentação, permissões, contextos, contatos, funis, oportunidades, tarefas, canais, relatórios, conversas e mensagens | TanStack Query atrás de `shared/server-state` |
| interface | menu aberto, formulário aberto, item selecionado, filtro “só abertas” | `useState` do componente |
| formulário | valores, envio e erro de contato/tarefa/canal/onboarding/login | componente do formulário; F6 amadurece a política |
| URL | rota, retorno seguro pós-login e futuro filtro compartilhável | React Router; filtros atuais ainda são locais |

Resposta do servidor não é copiada para store global. `TenantPresentationContext`
é uma fachada de consumo; apresentação, permissões e contextos vivem no mesmo
cache segregado das demais consultas.

## Chaves e autorização

`chaveDoRecurso` sempre inclui, nesta ordem:

1. tenant;
2. unidade (`tenant-inteiro` enquanto não houver unidade ativa);
3. identidade do usuário;
4. recurso;
5. parâmetros relevantes, como busca, funil, conversa e `apenasAbertas`.

`UNIT` existe no contrato organizacional, mas o ADR-0008 registra que os
agregados ainda não possuem `unit_id`. Por isso a interface consome
`/organizacao/contextos`, usa o nome real do tenant e não oferece unidade como
contexto navegável até existir ativação e recorte ponta a ponta. Rotular os
mesmos dados do tenant como “Unidade Centro” seria incorreto e inseguro.

## Frescor e retenção

| Recurso | Fresco por | Retenção inativa | Atualização |
|---|---:|---:|---|
| apresentação | 5 min | 5 min | alteração de perfil ou recarga explícita |
| permissões/contextos | 30 s | 5 min | reconexão ou recarga explícita |
| funis | 60 s | 5 min | operação de funil |
| contatos/tarefas/canais/oportunidades | 30 s | 5 min | mutação do próprio recurso |
| visão geral | 30 s | 5 min | mutações que alteram seus indicadores |
| conversas | 10 s | 5 min | push/reconexão/envio |
| mensagens | 5 s | 5 min | push/reconexão/envio |

Refetch por foco fica desligado; reconexão atualiza. A camada HTTP já possui
retry limitado para GET, então a biblioteca não multiplica tentativas.
Atualização normal em segundo plano mantém o conteúdo sem banner. Erro visível
é reservado à carga inicial/mutação ou à condição material do tempo real.

## Invalidação e mutações

- contato → contatos + visão geral;
- tarefa → tarefas + visão geral;
- oportunidade → oportunidades + visão geral;
- canal → canais + visão geral;
- mensagem → mensagens da conversa + lista de conversas;
- perfil inicial → substituição direta da apresentação retornada.

Somente alternar conclusão de tarefa é otimista. Exclusões, envio de mensagem,
movimento de oportunidade e demais escritas aguardam o servidor. A tarefa usa
snapshot, rollback e reconciliação determinísticos.

## Segurança e testes

O cache não possui persister. Logout local/remoto e nova sessão chamam a
limpeza síncrona antes de trocar a identidade; a fronteira também é recriada
por chave e apaga o cliente anterior ao desmontar.

`estadoServidor.test.tsx` cobre dimensões da chave, troca sem dado antigo,
resposta fora de ordem, rollback, invalidação seletiva e limpeza sem storage.
