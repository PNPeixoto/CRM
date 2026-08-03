# ADR-0007 — Autenticação local, TOTP e sessões limitadas

- Status: aceita
- Data: 2026-08-01

## Contexto

O padrão do projeto já aprovava identidade local com TOTP obrigatório para
administração. O Prompt 05 exigia confirmar a política na fonte vigente, sem
inventar federação, provedor ou segredo.

## Decisão

Manter autenticação local com Argon2id e pepper externo. Usar access token de
15 minutos e refresh rotativo por família, com uma hora de inatividade e 24
horas de duração absoluta. Logout encerra a família corrente; redefinição,
reuso e revogação explícita podem encerrar todas.

Exigir TOTP para `OWNER`, `ADMIN` e `SUPERADMIN`. O primeiro cadastro é uma
pré-autenticação por senha que não abre sessão; a confirmação gera códigos de
recuperação exibidos uma única vez. Segredos TOTP são cifrados por chave própria
e códigos aleatórios são persistidos somente por hash.

O rate limit não bloqueia uma conta globalmente: limita origem e par
conta+origem, preservando um sinal agregado para futura análise de risco.

## Consequências

O frontend precisa tratar os códigos `MFA_CADASTRO_NECESSARIO`, `MFA_NECESSARIO`
e `MFA_INVALIDO`. Produção precisa configurar entrega HTTPS de recuperação e
`MFA_SECRET_KEY`. TOTP cumpre o baseline aprovado, mas não é resistente a
phishing; WebAuthn/passkeys exige decisão e threat model próprios.

Access token já emitido possui janela residual máxima de 15 minutos após
revogação. Ações que exigirem corte imediato devem fazer validação adicional no
Prompt 06, usando `sid` e `amr` já presentes no contrato.
