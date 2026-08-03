# Prompt — infraestrutura, operação e LGPD

## Início do prompt

Revise infraestrutura, configuração, observabilidade, continuidade e privacidade
do CRM PNP. A revisão é somente leitura; não faça deploy, não destrua volumes,
não restaure sobre ambiente compartilhado e não exponha dados reais. Leia
`00-CONTEXTO-CANONICO.md`, compose/Dockerfiles, profiles, CI, documentos
operacionais, migrations e código de logs/auditoria. Use o modelo oficial.

### Containers e ambientes

- Build multi-stage, imagem base identificável, usuário não-root UID/GID 10001,
  filesystem read-only quando declarado e diretórios graváveis mínimos.
- Nenhum segredo em imagem, layer, compose, argumento de build, log ou arquivo
  versionado.
- Profiles dev/test/prod são separados; produção exige valores externos e falha
  fechada quando faltam.
- PostgreSQL 17/Redis 7 atuais do projeto, volumes, rede, portas, limites e health
  checks coerentes.
- Liveness mede processo; readiness mede dependências necessárias sem provocar
  carga ou vazar detalhe.
- Reverse proxy, TLS, forwarded headers, host confiável, CORS e origem WebSocket.

### Entrega e mudança

- Artefato é rastreável a commit, dependências e configuração.
- Promoção por ambiente não recompila de forma diferente.
- Migration ocorre com papel próprio, ordem e estratégia compatível com versões
  adjacentes da aplicação.
- Rollback da aplicação e roll-forward do banco possuem runbook e ensaio.
- Configuração de dev, seed e credencial de teste não alcançam produção.

### Observabilidade e resposta

- Logs estruturados incluem correlation id e identificadores sanitizados, nunca
  token, cookie, segredo, payload, mensagem ou dado pessoal desnecessário.
- Métricas cobrem latência, erros, saturação, fila, webhook, outbound, banco,
  sessão e rate limit sem cardinalidade explosiva.
- Traces preservam fronteiras e também minimizam dados.
- SLOs possuem indicador, alvo, janela e orçamento; alertas são acionáveis.
- Runbooks cobrem provedor fora do ar, backlog, banco/Redis, segredo comprometido,
  tenant suspeito e migration falha.

### Backup e recuperação

- Objetivos declarados: RPO ≤ 15 minutos e RTO ≤ 4 horas.
- Verifique escopo de backup, criptografia, retenção, acesso, cópia isolada e
  monitoramento.
- Evidência exige restauração em ambiente isolado, tempo medido, integridade e
  validação funcional; “backup concluído” não comprova restauração.
- Considere PostgreSQL, objetos/mídias, configuração indispensável e relações
  entre versões. Redis não deve ser fonte única de verdade.

### Escala e tempo real

- Broker STOMP em memória limita escala horizontal; documente a condição objetiva
  para substituição.
- Avalie afinidade de sessão, refresh, eventos, jobs e idempotência antes de mais
  de uma instância.
- Otimização precisa de baseline de latência, erro e saturação antes/depois.

### LGPD, auditoria e retenção

Monte um inventário por categoria: identidade, contato, conversa, canal,
autenticação, auditoria, logs, métricas e backups. Para cada uma registre:

- finalidade e hipótese de fundamento legal a validar com responsável jurídico;
- campos e minimização;
- origem, compartilhamento e acesso autorizado;
- retenção, legal hold e descarte/anonimização verificável;
- exportação, correção e eliminação quando aplicáveis;
- presença em logs, índices, caches e backups.

Auditoria append-only é separada de log operacional e obedece retenção explícita.
Não dê parecer jurídico; identifique lacunas técnicas e decisões que exigem
validação jurídica.

### Agente privado e integrações futuras

Se houver implementação, verifique conexão somente outbound, enrollment único,
mTLS, jobs e updates assinados, ausência de shell arbitrário e referências a
segredos em vez de valores. Se for apenas planejado, avalie o desenho sem marcar
como funcionalidade entregue.

### Saída

Entregue:

- matriz ambiente/configuração/segredo;
- mapa de SLO, métricas, alertas e runbooks;
- evidências de backup/restore/rollback ou lacunas explícitas;
- inventário técnico de dados e retenção;
- achados P0–P3;
- veredito dos Gates E e F e bloqueadores para piloto/produção.

Documentação de intenção não substitui exercício operacional medido.

## Fim do prompt

