# Revisão do pacote “excelência 100” recebido em 2026-08-05

## Veredito

O pacote não pode ser aplicado como diff. Ele é um conjunto de arquivos novos,
não uma comparação com conteúdo anterior, e mistura lacunas reais com notas,
cronogramas, responsáveis, depoimentos e estados não comprovados. Foram
aproveitadas apenas as propostas que têm correspondência direta no repositório.

## Decisão por arquivo

| Arquivo recebido | Decisão | Fundamentação |
|---|---|---|
| `contexto_plano-excelencia-100_diff.md` | rejeitado como plano canônico; itens reais tratados individualmente | a nota 82/100, os “+238 pontos”, prazos e projeções não possuem método, medição ou evidência; vários status já estavam desatualizados |
| `contexto_guia-onboarding-novos-devs_diff.md` | aceito após reescrita completa | a ausência do guia era real, mas a proposta usava JDK 21, Node 20, Maven na raiz, porta 5173, RabbitMQ e scripts/comandos inexistentes |
| `contexto_decisoes_ADR-0009-diagramas-c4_diff.md` | aceito após nova fundamentação e implementação | não havia visão visual; exemplos recebidos incluíam containers inexistentes, métricas e feedback sem fonte |
| `contexto_INDICE-CONTEXTOS-100_diff.md` | rejeitado | indexava o plano e o Prompt 29 inválidos, marcava documentos e operação como completos e concorria com `CLAUDE.md` como ponto de entrada canônico |
| `contexto_ENTREGA-ANALISE-100_diff.md` | rejeitado | é um recibo de uma análise externa, não contexto operacional; repete contagens, notas e status sem evidência |
| `contexto_resumo-executivo-100_diff.md` | rejeitado | deriva do mesmo score arbitrário e promete resultado/calendário sem baseline mensurável nem histórico de operação |
| `contexto_prompts_29-schema-drift-detection_diff.md` | rejeitado e não incluído no manifesto | duplica controles do Prompt 08, não respeita a sequência canônica e a implementação proposta não detecta o drift que promete |

## Constatações técnicas

### Detecção de schema

O Prompt 08 já implementou dois controles complementares:

- Flyway valida o histórico e os checksums das migrations ao iniciar/migrar;
- `SchemaVersionHealthIndicator` compara a versão estrutural esperada pela
  imagem com a maior versão aplicada e participa do readiness.

O código sugerido no Prompt 29 comparava um hash calculado do recurso com
`MigrationInfo.getChecksum()`, que é o checksum numérico mantido pelo Flyway,
não uma string SHA-256. Mesmo que os tipos fossem corrigidos, isso repetiria a
validação do Flyway e não detectaria DDL manual fora das migrations. Um job
diário também não “falha o CI”: ele executa numa aplicação já implantada. A
tabela de log, o endpoint administrativo e a alteração manual do histórico
acrescentariam estado e superfície sem cobrir o problema declarado.

Por isso `SEC-006` permanece resolvido no escopo definido pelo backlog: versão
da imagem, histórico e migrations controladas. Detecção de DDL arbitrário
exigiria outro problema, outra técnica e evidência de necessidade.

### Lacunas reais preservadas na trilha existente

- auditoria continua P0 e pertence ao Prompt 17;
- observabilidade e SLOs pertencem ao Prompt 22;
- escala horizontal e broker compartilhado pertencem ao Prompt 28;
- `unit_id` em dados de domínio exige definição de origem e backfill, conforme
  ADR-0008; não pode ser inferido com segurança nesta revisão;
- o seletor de contexto fabricado continua registrado no estado atual e deve
  ser tratado pela trilha frontend;
- a primeira execução externa do gate de segurança continua sendo `SEC-016`.

Antecipar essas entregas aqui quebraria a ordem dos manifestos e, no caso de
auditoria e `unit_id`, exigiria decisões de produto e migração não fornecidas.

## Mudanças aplicadas

1. Guia de onboarding baseado na stack e nos comandos executáveis, com JDK 25,
   Node 24, Maven Wrapper, portas 8080/5174 e Compose sem RabbitMQ.
2. ADR-0009 aceito e quatro visões Mermaid baseadas no código atual: contexto,
   containers, componentes de identidade e sequência de login/MFA.
3. Todas as entradas `uses:` do CI fixadas por SHA completo, resolvendo
   `SEC-013`; Dependabot continua acompanhando o ecossistema `github-actions`.
4. README, guias Windows/Linux, ASVS, backlog e estado atual alinhados. A
   exigência real de Node 24 corrigiu a documentação anterior que dizia Node 20.

## Critérios usados

- código e configuração executável vencem texto recebido;
- nenhuma nota ou prazo sem método e fonte;
- nenhuma infraestrutura futura desenhada como existente;
- nenhuma migration ou novo endpoint sem necessidade demonstrada;
- nenhuma duplicação fora dos manifestos canônicos;
- toda mudança de segurança precisa de evidência local verificável.

## Validação

Resultados preenchidos ao final desta revisão:

- referências móveis em `uses:`: nenhuma encontrada;
- links locais dos documentos novos: todos resolvidos;
- `git diff --check`: aprovado;
- backend `test -Pgate-rapido`: 51 testes, sem falha ou erro, incluindo
  `FronteiraDeModulosTest`;
- backend completo: compilou, mas o preflight interrompeu antes dos testes
  porque o daemon Docker estava indisponível; integração PostgreSQL/Redis não
  foi reaprovada nesta revisão;
- frontend: `api:check`, lint, 101 testes e build aprovados; permanecem os três
  avisos conhecidos de Fast Refresh.

