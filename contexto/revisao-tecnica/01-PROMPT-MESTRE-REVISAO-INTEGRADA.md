# Prompt mestre — revisão integrada do CRM PNP

Copie a partir de **“Início do prompt”** para executar uma revisão completa.

---

## Início do prompt

Você está na raiz do repositório CRM PNP. Faça uma revisão técnica integrada,
baseada em evidências, sem alterar código, configuração, migrations,
dependências, manifests ou documentação. O resultado é um diagnóstico; qualquer
correção dependerá de autorização posterior.

### Objetivo

Determinar se o estado atual é coerente com as regras do produto, preserva o
isolamento multi-tenant, respeita as decisões arquiteturais e possui evidência
suficiente para os gates declarados. Encontre causas e cenários concretos, não
uma lista genérica de boas práticas.

### Contexto e fontes obrigatórias

1. Leia integralmente `contexto/revisao-tecnica/00-CONTEXTO-CANONICO.md`.
2. Leia as fontes obrigatórias listadas na seção 9 desse documento.
3. Leia `contexto/revisao-tecnica/11-MODELO-DE-ACHADO.md` e use seu contrato.
4. Leia `contexto/revisao-tecnica/12-MATRIZ-DE-COBERTURA.md`.
5. Leia e execute, como roteiros de inspeção, os prompts 02 a 09 deste diretório.
6. Consolide tudo conforme o prompt 10.

Não confie apenas nos documentos. Inspecione o código, migrations, testes,
configuração e contratos realmente presentes. Se uma fonte divergir de outra,
registre o desvio e o impacto. Funcionalidade planejada não é defeito, exceto se
o produto, a UI, um contrato ou um gate a anunciar como pronta.

### Baseline antes da análise

Registre sem modificar o repositório:

- data/hora e fuso;
- branch, commit e estado resumido do working tree;
- versões disponíveis de Java, Maven, Node, npm, Docker e PostgreSQL pertinentes;
- manifests backend/frontend e respectivos status;
- migrations existentes e perfis de ambiente;
- comandos que serão executados e limitações do ambiente.

O working tree pode conter trabalho legítimo não versionado. Não descarte, não
reverta, não formate globalmente e não atribua automaticamente essas mudanças a
um defeito.

### Método obrigatório

Para cada dimensão:

1. derive as invariantes e os controles esperados;
2. localize a implementação real e os testes;
3. tente falsificar a regra com caso negativo, concorrência, troca de tenant,
   repetição, falha parcial ou entrada inválida, conforme aplicável;
4. registre apenas achados que tenham cenário e impacto;
5. classifique certeza e severidade pelo modelo oficial;
6. indique o menor teste de regressão que provaria a correção.

Faça rastreamento vertical das jornadas P0: interface → contrato HTTP/tempo real
→ autorização → aplicação/domínio → persistência/RLS → efeitos assíncronos →
observabilidade. Revise também fluxos de erro, reexecução e rollback.

### Verificações executáveis

Execute os comandos seguros disponíveis e registre resultado, duração e
limitação. No mínimo, quando o ambiente permitir:

- backend: `backend\mvnw.cmd test` a partir da raiz, ou `mvnw.cmd test` dentro de
  `backend`;
- frontend: `npm run api:check`, `npm run lint`, `npm test` e `npm run build`
  dentro de `frontend`;
- repositório: `git diff --check`;
- configuração de containers: validação declarativa do compose, sem subir ou
  destruir volumes desnecessariamente.

Não instale, atualize ou corrija dependências automaticamente. Não execute teste
destrutivo em ambiente compartilhado. Se credencial, serviço ou permissão
estiver ausente, registre a lacuna; não invente valor nem enfraqueça um controle.

### Atualidade das referências

Ao afirmar conformidade com ASVS, NIST, WCAG, LGPD, Spring, PostgreSQL ou uma
dependência, confira a versão vigente em fonte primária/oficial quando houver
acesso e registre data e versão. Se não houver acesso externo, identifique a
afirmação como pendente de validação; não use memória como prova de atualidade.

### Proteção de dados

Não exponha segredo, token, cookie, hash reutilizável, payload de webhook,
mensagem, arquivo ou dado pessoal. Use nomes de variáveis e exemplos
sanitizados. Não provoque envio real a clientes ou provedores.

### Entrega

Produza `contexto/revisao-tecnica/resultados/AAAA-MM-DD-revisao-integrada.md`
com esta estrutura:

1. **Veredito executivo:** máximo de 12 linhas; pronto ou não para demonstração,
   piloto controlado e produção, cada ambiente com justificativa.
2. **Baseline e limitações:** estado exato revisado e o que não pôde ser provado.
3. **Mapa de cobertura:** dimensão, arquivos inspecionados, testes executados e
   status `comprovado`, `falhou`, `parcial` ou `não verificado`.
4. **Achados:** primeiro P0, depois P1, P2 e P3, no formato obrigatório.
5. **Regras de negócio:** tabela de invariantes com resultado e evidência.
6. **Arquitetura e segurança:** desvios, ameaças e efeitos transversais.
7. **Gates A–F:** `aprovado`, `reprovado`, `aberto por planejamento` ou
   `não aplicável`, sempre com evidência e bloqueadores.
8. **Riscos aceitos e dívidas conhecidas:** separados de defeitos novos.
9. **Roadmap de correção:** ordem por dependência e redução de risco, com teste
   de saída para cada item; não estime datas sem dados de capacidade.
10. **Apêndice de evidências:** comandos, resultados sanitizados e referências.

Deduplicate achados que compartilham a mesma causa. Não dilua P0/P1 em uma lista
de estilo. Não declare gate aprovado por ausência de achados; exija a evidência
positiva definida em `contexto/prompts/GATES.md`.

Ao terminar, apresente no chat apenas o veredito, a contagem por severidade, os
bloqueadores e o caminho do relatório. Não aplique correções.

## Fim do prompt

