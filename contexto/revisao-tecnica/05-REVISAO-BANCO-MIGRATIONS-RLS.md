# Prompt — banco, migrations e RLS

## Início do prompt

Revise PostgreSQL, Flyway, integridade multi-tenant e RLS do CRM PNP. Trabalhe em
modo somente leitura; testes de banco devem usar ambiente descartável e
autorizado. Nunca limpe ou migre para trás um banco compartilhado. Leia
`00-CONTEXTO-CANONICO.md`, `backend/BANCO.md`, migrations, configuração de
profiles, testes de integração e ADRs. Use o modelo oficial de achado.

### Inventário e classificação

- Liste migrations em ordem, checksum quando disponível e objeto criado/alterado.
- Classifique cada tabela como global, tenant-scoped, operacional temporária,
  auditoria ou infraestrutura.
- Para toda tabela tenant-scoped, identifique coluna `tenant_id`, constraints,
  índices, política RLS e relacionamento composto.
- Confirme que migrations antigas não foram alteradas após uso; se não houver
  baseline verificável, registre a limitação em vez de presumir.

### Papéis e privilégios

- Separe `crm_migrator` e `crm_runtime`.
- Runtime não pode ter `SUPERUSER`, `BYPASSRLS`, ownership amplo nem privilégios
  de DDL.
- Revise grants/revokes, `PUBLIC`, sequences, schemas e funções.
- Funções `SECURITY DEFINER` precisam de `search_path` seguro, dono controlado,
  privilégios mínimos e validação de argumentos.
- Teste com o mesmo papel usado pela aplicação, não com usuário privilegiado.

### Contexto de tenant e pool

- Descubra como a aplicação define o tenant na conexão/transação.
- Verifique `SET LOCAL`/equivalente, início e término da transação e reset ao
  devolver conexão ao pool.
- Tente consulta sem tenant, tenant inválido, troca sequencial de tenants na mesma
  conexão, nested transaction, job assíncrono e consumidor de evento.
- Política deve usar contexto confiável e falhar fechada.

### RLS e integridade

- `ENABLE ROW LEVEL SECURITY` e `FORCE ROW LEVEL SECURITY` nas tabelas cabíveis.
- Policies cobrem `USING` e `WITH CHECK` para SELECT/INSERT/UPDATE/DELETE.
- Relações entre dados tenant-scoped impedem referência cruzada no próprio banco,
  preferencialmente por chave/constraint composta adequada.
- Teste leitura e escrita cross-tenant, inclusive joins, subqueries, aggregates,
  upsert, soft delete e funções.
- Verifique que seeds dev e testes não vazam para produção.

### Qualidade do schema

- UUID conforme decisão vigente; timestamps UTC; dinheiro em centavos.
- Nulos, defaults, checks, unicidade e FKs representam invariantes reais.
- Soft delete é considerado em unicidade, busca e relacionamentos.
- Dados de autenticação, canal e auditoria possuem proteção e retenção adequada.
- Idempotency keys e IDs de provedor têm escopo e unicidade corretos.
- Índices seguem consultas reais e incluem tenant quando necessário; evite tanto
  ausência crítica quanto índice especulativo.

### Evolução e recuperação

Em banco descartável, quando possível:

1. aplique V1 até a versão atual em banco vazio;
2. teste caminho de atualização de baseline representativo;
3. rode a aplicação/testes com papel runtime;
4. valide repetição segura e falha parcial;
5. inspecione locks e compatibilidade de deploy para migrations de tabelas
   relevantes;
6. relacione rollback de aplicação e estratégia de roll-forward do schema.

Não invente rollback destrutivo para Flyway. Mudanças incompatíveis devem ser
avaliadas com estratégia expand/contract quando aplicável.

### Saída

Entregue:

- catálogo `tabela | classe | tenant | RLS | integridade | evidência`;
- matriz `papel | privilégios efetivos | risco`;
- resultado dos caminhos banco vazio e upgrade;
- achados P0–P3;
- queries/planos que precisam de medição, sem otimização por intuição;
- veredito das partes de banco dos Gates A, D e E.

Qualquer possível bypass de RLS ou referência cross-tenant deve ser tratado como
bloqueador até ser falsificado por teste reproduzível.

## Fim do prompt
