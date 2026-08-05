# Sessão 2026-08-03 — revalidação do Gate B e login com MFA

- Branch: `main`
- Baseline: `4901499` mais working tree
- Objetivo: revalidar o Gate B e restaurar o login das contas de desenvolvimento
  sem remover a exigência de MFA nem recriar o banco

## Diagnóstico

As contas antigas continuavam ativas e com os hashes originais. O Prompt 05
transformou `peixoto` em `OWNER`, papel que exige MFA, mas o frontend não tinha
jornada para cadastrar ou informar o segundo fator. A impressão de banco
alterado era consequência desse bloqueio de interface.

Também foram encontrados: verificador do `npm audit` aprovando JSON de erro,
tipo OpenAPI gerado fora de sincronia e backend local em imagem antiga.

## Alterações

- Jornada de cadastro/ativação de TOTP, login com TOTP/recovery code e exibição
  única dos códigos de recuperação.
- Segredos e tokens permanecem apenas em memória; não entram em storage ou URL.
- Verificador de dependências falha fechado e ganhou três testes no CI.
- Tipo TypeScript OpenAPI regenerado.
- Serviço backend reconstruído sem remover ou recriar o volume PostgreSQL.
- `SEC-018` e `SEC-019` marcados como resolvidos.

## Evidência

- Backend: 112 testes verdes.
- Frontend: 93 testes verdes, lint sem erro e build de produção verde.
- Contrato: `npm run api:check` verde.
- Dependências: 0 críticas; duas altas com exceção nomeada e vigente.
- Segredos: 30 commits sem vazamento; achados da árvore limitados a logs
  ignorados de testes em `backend/target`.
- Runtime: backend `0.0.1-dev` saudável; banco preservado em V11 + seeds dev.

O relatório canônico está em
`contexto/revisao-tecnica/resultados/2026-08-03-revisao-gate-b-revalidacao.md`.
