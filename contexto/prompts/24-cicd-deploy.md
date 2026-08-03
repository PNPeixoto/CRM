---
id: "24"
title: "CI/CD, supply chain e deploy"
phase: "operations_compliance"
risk: "high"
prerequisites: ["02", "07", "22", "23"]
produces: ["pipeline verificável", "artefato rastreável", "rollback ensaiado"]
gate: "E"
---

# Prompt 24 — CI/CD e deploy

## Objetivo

Automatize gates, produção de artefato e promoção entre ambientes sem segredo no
pipeline e sem rebuild diferente por ambiente.

## Protocolo obrigatório

Não escolha provedor, estratégia de produção ou política de aprovação sem dado
existente; isso pode envolver contrato/segurança. Preserve o fluxo atual quando
reversível. Falha de teste não é ignorada. Evidência identifica commit e
artefato, mas nunca exibe segredo ou variável por valor.

## Trabalho

1. Ordene compile, testes, módulos, análise estática, dependency/secret scan,
   build, image scan e deploy de homologação; produção exige aprovação definida.
2. Gere SBOM, provenance/assinatura quando suportado e tag por digest/versão.
3. Promova o mesmo artefato; configuração/segredo vêm do ambiente seguro.
4. Use migrations backward-compatible e padrão expand/contract.
5. Configure deploy gradual/health gate e rollback de aplicação compatível com
   schema. Não prometa rollback destrutivo automático.
6. Integre evidências dos gates A–E e política formal de exceção/quarentena.

## Testes e aceite

- pipeline reproduz build a partir de checkout limpo;
- segredo de teste plantado faz secret scan quebrar;
- vulnerabilidade crítica simulada bloqueia imagem;
- deploy/rollback em homologação são ensaiados;
- artefato em execução aponta commit, versão, SBOM e resultado dos gates.
