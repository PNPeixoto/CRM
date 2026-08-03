# Pacote de revisão técnica — CRM PNP

Este diretório reúne prompts reproduzíveis para revisar o CRM PNP com base no
produto, nas regras de negócio, nas decisões arquiteturais e no código que
realmente existe. A revisão é **somente leitura por padrão**: ela produz achados
e evidências, mas não corrige arquivos sem uma autorização separada.

## Como usar

1. Abra uma nova sessão na raiz do repositório.
2. Entregue ao revisor o conteúdo de
   [`01-PROMPT-MESTRE-REVISAO-INTEGRADA.md`](01-PROMPT-MESTRE-REVISAO-INTEGRADA.md).
3. Para uma revisão menor, use somente o prompt especializado desejado.
4. Salve o relatório em `contexto/revisao-tecnica/resultados/` com o padrão
   `AAAA-MM-DD-<escopo>.md`.
5. Não marque um gate como aprovado sem evidência reproduzível.

O prompt mestre coordena todos os escopos. Os prompts especializados podem ser
executados isoladamente e já contêm os limites e o formato mínimo de saída.

## Arquivos

| Arquivo | Finalidade |
| --- | --- |
| `00-CONTEXTO-CANONICO.md` | Contexto consolidado do produto e da implementação atual |
| `01-PROMPT-MESTRE-REVISAO-INTEGRADA.md` | Revisão completa e consolidação dos resultados |
| `02-REVISAO-REGRAS-DE-NEGOCIO-E-DOMINIO.md` | Jornadas, invariantes e coerência do domínio |
| `03-REVISAO-ARQUITETURA-MODULAR.md` | Módulos, dependências, transações e evolução |
| `04-REVISAO-SEGURANCA-AUTENTICACAO-AUTORIZACAO.md` | Segurança, tenant, sessões, MFA e IDOR |
| `05-REVISAO-BANCO-MIGRATIONS-RLS.md` | PostgreSQL, Flyway, RLS e integridade |
| `06-REVISAO-APIS-OPENAPI-INTEGRACOES.md` | HTTP, OpenAPI, webhooks, canais e tempo real |
| `07-REVISAO-FRONTEND-UX-ACESSIBILIDADE.md` | Frontend, estados de interface e acessibilidade |
| `08-REVISAO-TESTES-CI-DEPENDENCIAS.md` | Estratégia de testes, CI e cadeia de suprimentos |
| `09-REVISAO-INFRA-OPERACAO-LGPD.md` | Deploy, observabilidade, recuperação e privacidade |
| `10-CONSOLIDACAO-VEREDITO-E-ROADMAP.md` | Deduplicação, gates, veredito e plano de correção |
| `11-MODELO-DE-ACHADO.md` | Contrato uniforme para registrar evidências |
| `12-MATRIZ-DE-COBERTURA.md` | Mapa entre risco, prompt e evidência esperada |

## Regras de execução

- Trabalhe sobre o estado atual, inclusive alterações ainda não versionadas.
- Não faça `reset`, descarte, formatação global, migration corretiva ou alteração
  de dependência durante a revisão.
- Não copie segredos, tokens, cookies, dados pessoais, mensagens ou payloads de
  clientes para o relatório. Use valores sanitizados.
- Uma afirmação sem arquivo e linha, teste reproduzível ou artefato verificável é
  hipótese, não achado confirmado.
- Funcionalidade planejada não é defeito do estado atual, salvo quando a UI, a
  documentação ou um gate afirmar que ela já está pronta.
- Se documentação e execução divergirem, registre o desvio; não esconda a
  divergência escolhendo silenciosamente uma das fontes.

## Saída esperada

O relatório consolidado deve começar pelo veredito, separar fatos de hipóteses,
priorizar riscos reais e terminar com uma sequência executável de correções. O
formato obrigatório de cada achado está em
[`11-MODELO-DE-ACHADO.md`](11-MODELO-DE-ACHADO.md).

