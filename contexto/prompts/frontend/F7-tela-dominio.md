---
id: "F7"
canonical_id: "frontend:F7"
title: "Tela de domínio repetível"
phase: "frontend_product"
risk: "medium"
prerequisites: ["frontend:F5", "frontend:F6"]
blocking: "before-demo"
produces: ["fatia vertical de interface", "estados completos", "testes de jornada"]
gate: "C"
repeatable: true
---

# F7 — Tela de domínio

## Objetivo

Entregue uma tela real por execução, de ponta a ponta, sem transformar o modelo
em uma tabela universal ou criar abstrações antes do segundo consumidor.

## Parâmetros obrigatórios da execução

- identificador qualificado, por exemplo `frontend:F7:contacts`;
- rota e jornada principal;
- ator/permissões;
- endpoints e contrato disponíveis;
- critérios específicos do domínio.

## Trabalho

1. Implemente lista com paginação, filtro e ordenação essenciais, estado na URL
   quando compartilhável e estados loading/empty/error/success/forbidden.
2. Só adicione colunas configuráveis, visões salvas, seleção em massa, edição
   inline ou exportação quando a jornada e o backend já sustentarem o caso.
3. Escolha página ou drawer para detalhe conforme profundidade e navegação; não
   imponha um padrão único a todos os domínios.
4. Cubra criar/editar somente quando autorizados, usando F6 e revalidando dados
   pelo servidor após mutação.
5. Aplique permissões, anti-enumeração, responsividade, teclado, foco e linguagem
   clara em todos os estados.
6. Prepare internacionalização sem exigir tradução agora: strings centralizáveis,
   sem concatenação frágil e datas/números pelo formatador comum.
7. Entregue testes unitários/de integração no mesmo PR e E2E apenas para a
   jornada crítica conforme F12.

## Aceite

- a execução registra parâmetros e evidência própria;
- estados principais funcionam em desktop, mobile e teclado;
- filtro/página compartilháveis sobrevivem a recarga quando aplicável;
- autorização não depende da ausência de um botão;
- não surge componente genérico sem consumidores e variações comprovados.

