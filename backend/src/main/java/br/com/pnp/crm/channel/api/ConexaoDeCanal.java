package br.com.pnp.crm.channel.api;

import java.util.UUID;

/**
 * Conexão de canal, na forma em que outros módulos a enxergam.
 *
 * <p>Não é a entidade JPA. O módulo {@code conversation} precisa saber a qual
 * tenant e a qual provedor uma conexão pertence; não precisa — e não deve —
 * conhecer o formato de armazenamento, nem receber um objeto gerenciado por
 * uma sessão do Hibernate que pertence a outro módulo.
 */
public record ConexaoDeCanal(UUID id, UUID tenantId, TipoCanal tipo, String nome) {
}
