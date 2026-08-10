# ADR-0013 — Auditoria: integridade verificável e política de falha

- Status: accepted
- Data: 2026-08-09
- Relacionada: ADR-0002

## Contexto

O Prompt 17 exige uma trilha de negócio separada dos logs, append-only,
consultável por permissão própria e sem conteúdo sensível. Também exige uma
política explícita quando a escrita da auditoria falha. Tratar toda falha do
mesmo modo criaria dois riscos opostos: confirmar uma mudança crítica sem
rastro ou transformar uma negação de acesso correta em indisponibilidade.

## Decisão

1. Eventos usam catálogo canônico e versionado. A API interna recebe somente
   enums e identificadores tipados; não aceita texto livre, payload ou detalhe.
2. Mudanças de credencial, papel, configuração sensível, exportação e leitura
   da própria trilha são **fail-closed**. A auditoria participa da transação da
   mudança; sem evento, a operação sofre rollback.
3. Negação de autorização é **best effort** em transação independente. Uma
   falha da trilha não troca o `403` correto por erro de infraestrutura.
4. `audit_event` permite `SELECT` e `INSERT` ao runtime, mas revoga `UPDATE` e
   `DELETE`. Um gatilho também rejeita mutação comum. Correção é outro evento.
5. Cada linha recebe SHA-256 sobre uma representação canônica de seus
   metadados. A leitura recalcula e falha fechada em divergência. Isso detecta
   alteração acidental ou administrativa; não é apresentado como assinatura,
   cadeia criptográfica ou imutabilidade contra superusuário.
6. A tabela registra uma categoria de retenção, sem prazo. O ponto de extensão
   para expurgo controlado não concede exclusão ao runtime; política, prazo,
   legal hold e função privilegiada pertencem ao Prompt 18.
7. Identidade humana é preservada por UUID sem chave estrangeira ao usuário.
   A consulta pode enriquecer com o nome atual, mas o evento sobrevive à
   remoção da conta.

## Metadados permitidos

- tenant, instante, ator humano/sistema e escopo;
- ação versionada, tipo/id do alvo, resultado e motivo canônico;
- correlação, categoria de retenção, versão e hash de integridade.

Token, cookie, segredo, corpo, mensagem, arquivo, cabeçalho, resposta e URL
renderizada não fazem parte do contrato nem do schema.

## Alternativas descartadas

- Auditoria somente em log: não oferece consulta, isolamento e proteção de
  mutação com semântica de negócio.
- Texto livre ou JSON de detalhes: facilitaria copiar segredo e dado pessoal.
- Best effort para tudo: confirmaria mudança crítica sem evidência.
- Fail-closed para toda negação: permitiria causar indisponibilidade ao forçar
  tentativas que já deveriam terminar em `403`.
- Chamar o hash de imutabilidade criptográfica: promessa que o modelo não
  sustenta contra o dono do banco.

## Consequências e revisão

Novas ações críticas precisam entrar no catálogo e chamar a porta mínima
dentro da mesma transação. Exportação e gestão de papéis já possuem eventos
reservados, mas seus fluxos de negócio serão conectados quando os respectivos
prompts criarem esses endpoints. Revisar ao implementar o Prompt 18 ou caso se
adote assinatura externa, encadeamento ou armazenamento WORM.

## Evidências

- `backend/src/main/resources/db/migration/V22__auditoria_append_only.sql`
- `backend/src/main/java/br/com/pnp/crm/audit/`
- `backend/src/test/java/br/com/pnp/crm/audit/internal/AuditoriaCorporativaTest.java`
- `frontend/src/pages/audit/AuditPage.tsx`

