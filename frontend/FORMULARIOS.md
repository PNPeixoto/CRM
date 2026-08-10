# Formulários, validação e erros

O formulário de contato é a implementação de referência do padrão F6. A regra
vale para novos formulários e deve ser adaptada ao domínio, sem transformar
todas as telas em um componente universal.

## Fronteira de validação

- validações locais cobrem formato, presença e limites simples para responder
  sem espera;
- o servidor continua sendo a autoridade para unicidade, autorização e regras
  de negócio;
- `mapearFalhaDeFormulario` aceita somente nomes de campo allowlisted, associa
  mensagens RFC 9457 ao controle e mantém o restante como erro global seguro;
- os valores permanecem no estado quando a submissão falha e o foco segue para
  o primeiro campo inválido na ordem visual.

O botão fica indisponível durante o envio e uma trava síncrona impede duas
submissões antes do próximo render. Operações repetíveis também enviam
`Idempotency-Key`; a criação de contato persiste a chave e devolve o mesmo id em
replay do mesmo conteúdo.

## Semântica dos dados

- data civil permanece `YYYY-MM-DD` e não passa por `Date` com fuso implícito;
- `datetime-local` só vira instante com um fuso IANA explícito;
- dinheiro é `{ amountMinor, currency }`; o parser usa texto e `BigInt`, só
  devolve inteiro seguro e nunca multiplica um decimal em ponto flutuante;
- segredo começa vazio, branco significa “não alterar” e respostas exibem
  apenas presença/ausência, nunca o valor ou uma máscara reutilizável.

## Evidência mínima

Cada formulário precisa testar erro global e por campo, foco/teclado, preservação
de valores, submissão concorrente e acessibilidade automatizada. Formulários com
tempo, dinheiro ou segredos acrescentam casos de fusos distintos, centavos,
limite seguro, moeda e segredo em branco.
