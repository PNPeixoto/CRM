# Prompt — regras de negócio e domínio

## Início do prompt

Revise as regras de negócio e a coerência do domínio do CRM PNP. A análise é
somente leitura. Comece por `00-CONTEXTO-CANONICO.md`, documentos de produto,
glossário, modelo organizacional e ADRs. Depois confronte cada regra com código,
migration, contrato e teste. Use `11-MODELO-DE-ACHADO.md`.

### Pergunta central

O sistema impede estados impossíveis e mantém resultados corretos quando há
troca de tenant/unidade, repetição, concorrência, falha parcial, reabertura e
dados antigos?

### Cobertura obrigatória

#### Identidade organizacional

- Diferencie usuário interno, contato, tenant, unidade, equipe, membership,
  papel, permissão e escopo em todo o modelo.
- Verifique vigência e estado ativo de usuário/membership/unidade.
- Tente atribuir registros a responsável de outro tenant, inativo ou fora do
  escopo.
- Confirme os alcances TENANT, UNIT e OWN e identifique onde NETWORK/TEAM ainda
  são somente planejamento.

#### Contatos

- Tipos pessoa e organização, obrigatoriedade condicional e normalização de
  campos vazios.
- Duplicidade e identidade por canal sem unir pessoas indevidamente.
- Responsável, atualização parcial, busca/paginação, exclusão lógica e
  comportamento de referências existentes.
- Casos cross-tenant e acesso após exclusão.

#### Funis e oportunidades

- Uma etapa pertence ao funil e tenant corretos; contato e responsável também.
- Transições aberto/ganho/perdido/reaberto mantêm `closed_at` e motivo de perda
  coerentes.
- Valor em centavos, limites, nulos e arredondamento nas bordas HTTP/UI.
- Ordem das etapas, concorrência em movimentação e exclusão lógica.
- Criação única do funil padrão, apenas depois do onboarding, inclusive com duas
  requisições simultâneas.

#### Tarefas

- Coerência entre responsável, contato e oportunidade.
- Concluir, reabrir e editar não aceitam data/estado contraditório do cliente.
- Datas UTC, atraso, filtros, atualização concorrente e exclusão lógica.

#### Conversas, mensagens, canais e roteamento

- Resolver tenant pelo canal autenticado; nunca pelo payload externo.
- Deduplicar inbound e outbound sem descartar evento legítimo.
- Persistir mensagem inbound antes do ACK; mensagem outbound nasce pendente e
  alcança estado final rastreável.
- Garantir ordem e consistência sob mensagens simultâneas e retry do provedor.
- Relacionar contato, conversa, unidade, responsável e oportunidade sem cruzar
  tenants.
- Verificar conclusão/reabertura, histórico e falha de roteamento.

#### Onboarding e presets

- Segmentos suportados, rótulos e funil padrão corretos.
- Mesma escolha é idempotente; alteração posterior é conflito controlado.
- Preset de navegação não concede capability, entitlement, permissão ou escopo.
- Estado parcial de onboarding e concorrência não deixam tenant inconsistente.

#### Relatórios

- Métricas derivam das mesmas regras transacionais e APIs públicas dos módulos.
- Conversão = ganhos / (ganhos + perdidos), excluindo abertos e tratando divisor
  zero; arredondamento em uma casa decimal.
- Datas, timezone, soft delete, tenant, escopo e filtros são coerentes entre
  dashboard e telas operacionais.

### Jornadas verticais mínimas

Trace com arquivos e linhas:

1. login → contexto organizacional → navegação permitida;
2. onboarding → funil padrão → primeira oportunidade;
3. contato → oportunidade → movimentação → relatório;
4. tarefa → conclusão → reabertura;
5. webhook inbound → contato/conversa/mensagem → inbox/WebSocket;
6. resposta outbound → fila/provedor → estado final/erro sanitizado.

Para cada jornada, liste pré-condição, comando/ação, invariantes, resultado
esperado, evidência existente e teste ausente. Inclua ao menos um caso feliz, um
caso inválido e um caso de repetição ou concorrência.

### Saída

Entregue:

- tabela `Regra | Implementação | Teste | Resultado | Evidência`;
- achados no formato oficial;
- regras declaradas mas não implementadas, separadas de defeitos;
- contradições de terminologia ou documentação;
- cinco testes de regressão de maior valor, ordenados por risco.

Não proponha um modelo de domínio novo sem demonstrar uma invariante que o
modelo atual não consegue proteger.

## Fim do prompt

