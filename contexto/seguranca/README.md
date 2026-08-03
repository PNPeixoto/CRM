# Baseline de segurança — CRM PNP

Produto do Prompt backend 07. Três documentos, um propósito: transformar
segurança em requisito verificável, com apontamento para código e teste reais.

| Documento | Conteúdo |
|---|---|
| [`asvs-5.0.0-matriz.md`](asvs-5.0.0-matriz.md) | Matriz `controle · aplicável · implementação · teste · evidência` sobre os 16 capítulos |
| [`threat-models.md`](threat-models.md) | Ameaça por fluxo: ativo, fronteira, abuso, impacto, controle, teste, risco residual |
| [`backlog.md`](backlog.md) | Achados priorizados, com responsável, prazo e fundamento |

## Padrão adotado

| Item | Valor |
|---|---|
| Padrão | OWASP Application Security Verification Standard |
| Versão | **5.0.0** |
| Data de publicação | **30 de maio de 2025** |
| Fonte oficial | <https://owasp.org/www-project-application-security-verification-standard/> |
| Estrutura | 16 capítulos; identificador `<capítulo>.<seção>.<requisito>`; coluna `L` indica o nível 1, 2 ou 3 |
| Fonte da estrutura | CSV oficial da 5.0.0 no repositório OWASP/ASVS |
| Consultado em | 2026-08-03 |

**Alvo: nível 2.** O nível 3 é aplicado apenas onde o impacto justifica e está
marcado como tal na matriz — hoje em isolamento entre tenants e em proteção de
credencial de canal, que são os dois pontos onde uma falha atinge todos os
clientes de uma vez em vez de um.

A versão foi lida da fonte oficial nesta execução; nada aqui vem de
documentação memorizada, conforme o protocolo do prompt.

## Método e limites

O escopo é o que existe hoje: 30 rotas HTTP, um endpoint WebSocket, dois
workers e um webhook de provedor. Fluxo que o produto ainda não tem — mídia,
automação, conector, exportação, billing, agente privado — aparece nos threat
models **declarado como inexistente**, com a fronteira que precisará ser
tratada quando for construído. Um modelo de ameaça sobre componente imaginário
descreve o sistema que gostaríamos de ter, e é pior que a ausência dele porque
parece cobertura.

Não foram executados: teste de intrusão, fuzzing, análise dinâmica, auditoria
de acessibilidade e teste de carga. A matriz distingue **verificado por
execução** de **verificado por inspeção** em cada linha; o segundo caso é
afirmação sobre o código lido, não sobre comportamento observado.

## Veredito do Gate B

> **Atualização de 2026-08-03, após F4A e F4.** O lado frontend foi executado:
> CSP verificada no navegador com violação `enforce` reportada pelo próprio
> Chrome, ausência de execução genérica coberta por teste de contrato, cadeia
> de build fixada, e um redirecionamento aberto encontrado e corrigido
> (`SEC-014`). **O Gate B está fechado nos dois lados.**

**Aprovado do lado backend.** Nenhum achado crítico permaneceu aberto. Os dois
riscos altos encontrados foram tratados nesta execução:

- `CVE-2026-59889` em `jackson-databind`, *Incorrect Authorization*, CVSS 6.5,
  no caminho de desserialização de toda requisição — **corrigido** por bump
  para 3.1.5 e 2.21.5;
- `GHSA-qwww-vcr4-c8h2` em `react-router` — **não aplicável**, comprovado pelo
  aviso oficial, com exceção nomeada, responsável e prazo.

Do lado frontend, o F4A e o F4 fecharam a metade que faltava. O detalhe da
superfície de navegador está em `frontend/SEGURANCA-NAVEGADOR.md`, inclusive o
que **não** é código do frontend: o servidor de estáticos precisa enviar a CSP
como cabeçalho, mais `frame-ancestors`, que o navegador ignora quando vem em
`<meta>`. Enquanto esse servidor não existir no repositório, o clickjacking
depende de configuração externa não versionada — está registrado como limite.

Riscos médios e baixos estão no backlog com responsável e prazo. Nenhum foi
aceito informalmente — a exceção de dependência é verificada por máquina e
reprova o CI quando o prazo vence.
