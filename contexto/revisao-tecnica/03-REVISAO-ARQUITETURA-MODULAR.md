# Prompt — arquitetura e monólito modular

## Início do prompt

Revise a arquitetura do CRM PNP como monólito modular Spring Modulith. A revisão
é somente leitura e deve comparar a arquitetura declarada com dependências e
fluxos reais. Leia `00-CONTEXTO-CANONICO.md`, os ADRs e os padrões técnicos.
Registre achados conforme `11-MODELO-DE-ACHADO.md`.

### Questões a responder

- As fronteiras reduzem acoplamento sem criar cerimônia artificial?
- Cada regra está no módulo que possui seus dados e sua linguagem?
- Dependências síncronas, eventos e transações têm semântica explícita sob falha?
- A implementação atual consegue evoluir os próximos prompts sem reescrita ou
  vazamento de segurança?

### Inspeção obrigatória

#### Mapa de módulos

- Inventarie módulos realmente existentes, suas APIs públicas e os módulos
  apenas previstos.
- Gere uma matriz `origem → destino → mecanismo → justificativa`.
- Procure imports de entidades, repositories, implementações ou tabelas privadas
  de outro módulo.
- Execute/verifique testes estruturais do Spring Modulith e compare o resultado
  com o mapa pretendido.
- Identifique dependência circular, pacote compartilhado crescente e “API” que
  expõe detalhes de persistência.

#### Camadas e domínio

- Controllers tratam HTTP; casos de uso coordenam; domínio protege invariantes
  reais; repositories persistem.
- DTOs de entrada/saída não vazam entidades nem aceitam mass assignment.
- CRUD simples não foi inflado artificialmente, e regra complexa não ficou
  espalhada em controller, mapper e repository.
- Exceções internas não atravessam a fronteira pública.

#### Transações, eventos e concorrência

- Mapeie o limite transacional das jornadas P0.
- Verifique efeitos externos dentro de transação e alterações locais fora de
  uma transação necessária.
- Para eventos, determine publicação, persistência, consumidor, retry,
  idempotência, ordenação e tratamento de falha permanente.
- Revise concorrência no onboarding/funil padrão, inbound, outbound, refresh de
  sessão e mudanças de estado.
- Avalie locks/advisory locks por chave estável e risco de deadlock ou contenção.

#### Contratos entre módulos

- `OrganizationAccess` deve concentrar o acesso organizacional pertinente.
- Relatórios devem usar APIs públicas, não consultar tabelas privadas.
- Adapters de canal devem normalizar provedores sem contaminar o núcleo.
- A fronteira do OpenAPI gerado deve permanecer no adapter frontend.
- Capability, entitlement, autorização e navegação não podem colapsar numa única
  flag compartilhada.

#### Evolução e operação

- Separe limitação aceitável do monólito atual de bloqueador real.
- Avalie o broker STOMP em memória como restrição declarada de escala, sem exigir
  microserviços prematuros.
- Verifique pontos únicos de falha, filas locais, caches como fonte indevida de
  verdade e dependências de profile dev.
- Identifique decisões arquiteturais relevantes ainda sem ADR ou ADR divergente
  do código.

### Evidência e saída

Entregue:

1. mapa textual dos módulos implementados e planejados;
2. matriz de dependências com `permitida`, `questionável` ou `violação`;
3. três rastreamentos transacionais completos: autenticação, inbound e criação
   do funil padrão;
4. achados no formato oficial;
5. dívidas deliberadas com condição objetiva para revisitá-las;
6. avaliação de prontidão dos Gates A, D e F, sem aprovar por intenção.

Não recomende separar serviço, adicionar fila ou criar abstração sem indicar o
problema mensurado, a alternativa mínima e o limiar que justificaria a mudança.

## Fim do prompt

