# Modelo de achado

Use um bloco como este para cada achado. Não combine causas diferentes apenas
porque aparecem na mesma tela ou arquivo.

## `[P0|P1|P2|P3] AREA-NNN — Título específico e verificável`

- **Tipo:** defeito | vulnerabilidade | regra de negócio | desvio arquitetural |
  contrato | privacidade | operação | documentação | evidência ausente |
  planejado/não implementado
- **Certeza:** confirmado | provável | hipótese a validar
- **Gate afetado:** A | B | C | D | E | F | nenhum
- **Regra ou requisito:** regra concreta que deveria ser atendida
- **Evidência:** `caminho/do/arquivo:linha` e, quando aplicável, comando/teste e
  resultado sanitizado
- **Cenário de reprodução ou ataque:** pré-condições, ação e resultado observado
- **Resultado esperado:** comportamento correto e mensurável
- **Impacto:** efeito técnico e efeito para cliente/operação
- **Abrangência:** tenants, perfis, fluxos e dados afetados
- **Correção mínima recomendada:** menor mudança capaz de remover a causa
- **Teste de regressão:** teste que falha antes e passa depois da correção
- **Dependências ou bloqueios:** decisão, fornecedor, migration ou outro achado

## Severidade

- **P0 — bloqueador:** isolamento entre tenants quebrado, bypass de autenticação
  ou autorização, exposição de segredo/dado sensível, perda ou corrupção ativa de
  dados, execução remota, migration que impede deploy/rollback seguro, ou falha
  crítica em uma jornada P0 sem contenção aceitável.
- **P1 — alto:** violação provável de segurança ou regra central, inconsistência
  relevante, indisponibilidade ampla, falha de integração com impacto operacional
  ou ausência de controle indispensável para piloto/produção.
- **P2 — médio:** defeito importante mas contornável, risco de manutenção,
  acessibilidade relevante, contrato ambíguo ou evidência insuficiente sem
  exploração imediata demonstrada.
- **P3 — baixo:** melhoria localizada de clareza, consistência, eficiência ou
  experiência, sem risco relevante no estado atual.

Severidade mede impacto e probabilidade, não esforço de correção. “Boa prática”
sem cenário, impacto e evidência não deve virar achado.

## Regras de evidência

- Prefira arquivo e linha exatos. Para comportamento, acrescente teste ou passos
  reproduzíveis.
- Evidência de teste registra commit/baseline, ambiente, data, comando, resultado
  e artefato, sem dados sensíveis.
- Não publique token, cookie, segredo, payload de cliente ou dado pessoal.
- Se não foi possível executar, diga por quê e mantenha a certeza como provável
  ou hipótese.
- Achado planejado/não implementado não recebe severidade de defeito, salvo se um
  contrato público, gate ou interface anunciar a função como disponível.

## Resumo tabular

| ID | Sev. | Tipo | Título | Evidência principal | Gate | Certeza |
| --- | --- | --- | --- | --- | --- | --- |
| AREA-001 | P1 | regra de negócio | Exemplo | `arquivo:42` | C | confirmado |

