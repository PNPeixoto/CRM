package br.com.pnp.crm.shared.api;

import java.util.UUID;

/** Sinal interno para encerrar sockets após logout, reset ou reuso de token. */
public record SessaoTempoRealRevogada(
        UUID tenantId,
        UUID usuarioId,
        UUID sessaoId) {
}
