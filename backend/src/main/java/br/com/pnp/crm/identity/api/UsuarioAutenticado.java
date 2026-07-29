package br.com.pnp.crm.identity.api;

import java.util.UUID;

/**
 * Identidade do usuário da requisição, para consumo de outros módulos.
 *
 * <p>Não expõe hash de senha, status de bloqueio nem qualquer campo que só
 * interesse ao {@code identity}. Um módulo que receba a entidade inteira passa
 * a depender do formato dela, e a fronteira deixa de existir na prática mesmo
 * que o compilador não reclame.
 */
public record UsuarioAutenticado(UUID id, UUID tenantId, String login, String nomeCompleto) {
}
