# Sessão 2026-08-08 — backend:11 onboarding e segmentos

- Branch observada: `main`
- Ambiente: Windows, Node 24, npm 11, JDK 25, Docker disponível
- Responsável: Codex

## Entregue

- perfil inicial continua persistido em `tenant_profile`, com preset/versionamento
  e RLS já existentes desde V7; nenhuma migration artificial foi criada;
- `PUT /api/empresa/perfil-inicial` serializa conclusões pelo tenant e agora
  materializa o funil e suas etapas na mesma transação;
- evento público e imutável `PerfilInicialConcluido` desacopla os módulos
  `tenant` e `deal`; o listener é síncrono para propagar falha e garantir
  rollback conjunto de perfil, funil e etapas;
- duas conclusões simultâneas do mesmo segmento convergem para um perfil, um
  funil padrão e um único conjunto de etapas;
- `PUT /api/empresa/apresentacao` permite ao administrador trocar o segmento
  depois do onboarding sem reescrever funil, etapas ou dados personalizados;
- alteração de apresentação antes do perfil inicial falha fechada com
  `PERFIL_INICIAL_PENDENTE`;
- catálogo passa a resolver a versão efetivamente persistida e rejeita versão
  desconhecida, em vez de aplicar silenciosamente o preset atual;
- IDs estáveis do catálogo continuam alimentando a mesma função de resolução
  usada pelo menu e pelo roteador; apresentação permanece separada de permissão;
- OpenAPI e contrato TypeScript foram regenerados;
- onboarding ganhou prova de acessibilidade automatizada e de recarga imediata
  da navegação após a escolha, sem novo login.

## Evidências

- backend completo: 132 testes, 0 falhas, 0 erros e build verde;
- integração real: isolamento RLS, concorrência, atomicidade observável,
  preservação de funil personalizado, autorização e bloqueio pré-onboarding;
- fronteiras Spring Modulith verdes com o novo evento público;
- frontend completo: 123 testes em 27 arquivos, todos verdes;
- build de produção, lint sem erros e `api:check` verdes; permanecem os três
  avisos preexistentes de Fast Refresh;
- uma primeira execução focada encerrou a JVM do runner antes das asserções;
  o recorte foi repetido isoladamente e depois pelo gate completo, ambos verdes.

## Decisões e rollback

O segmento altera defaults de apresentação, não autorização nem fronteira de
produto. A troca explícita adota a versão corrente do preset, mas nunca reaplica
o funil inicial. Uma futura versão é adicionada ao catálogo sem remover as
anteriores enquanto houver perfil referenciando-as.

Não houve alteração de schema. O rollback é apenas do binário: V14 permanece
compatível e os funis já materializados continuam válidos. A versão anterior
ignora o novo endpoint e conserva sua criação preguiçosa como recuperação.

## Gate C e próximo passo

O Prompt 11 está concluído. O Gate C ainda não deve ser declarado fechado: o
critério canônico exige uma jornada crítica E2E reproduzível, e o repositório
ainda não possui runner E2E. Testes de integração e componente não foram
rotulados indevidamente como E2E.

O próximo prompt backend liberado é `backend:12`. A lacuna do Gate C deve ser
tratada em `frontend:F12` ou em execução específica de evidência antes de demo.
