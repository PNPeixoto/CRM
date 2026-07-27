# Registro de decisões

> **Append-only.** Nunca edite nem apague uma entrada.
> Decisão revertida entra como entrada NOVA referenciando a antiga.
>
> Formato:
>
> ## AAAA-MM-DD — Título
> **Decisão:** o que foi escolhido
> **Alternativas descartadas:** o que foi considerado e por que não
> **Consequência:** o que isso fecha ou obriga daqui em diante

## 2026-07-27 — Monólito modular em vez de microsserviços

**Decisão:** Spring Modulith, aplicação única, fronteira por pacote.
**Alternativas descartadas:** microsserviços — custo operacional
incompatível com equipe de uma pessoa.
**Consequência:** fronteira só existe se for verificada no CI
(`ApplicationModules.verify()`).

## 2026-07-27 — Registro central de rotas com status por página

**Decisão:** `app/routes.ts` alimenta roteador e navegação, com campo
`status` controlando o placeholder "em produção".
**Alternativas descartadas:** rotas espalhadas pelos componentes — a
navegação sairia do ar com o roteador sem ninguém perceber.
**Consequência:** adicionar página é adicionar uma linha; nunca duplicar
a lista de rotas em outro lugar.
