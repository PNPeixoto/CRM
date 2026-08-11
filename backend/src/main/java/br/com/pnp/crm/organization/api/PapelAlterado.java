package br.com.pnp.crm.organization.api;

import java.util.UUID;

/**
 * Mudanca em papel ou atribuicao, publicada para quem registra auditoria.
 *
 * <p><b>Por que evento, e nao uma chamada direta a trilha.</b> O modulo de
 * auditoria ja depende de {@code organization.api}: ele le
 * {@link Autorizacao} no proprio controller e escuta
 * {@link AcessoOrganizacionalNegado}. Chamar {@code AuditTrail} daqui fecharia
 * um ciclo entre os dois modulos, e o {@code FronteiraDeModulosTest} recusa —
 * com razao, porque ciclo entre modulos e o primeiro passo para os dois viram
 * um so.
 *
 * <p>A direcao correta ja estava estabelecida: organization <b>publica</b>,
 * audit <b>escuta</b>. Este evento segue o mesmo caminho de
 * {@code AcessoOrganizacionalNegado}, em vez de inventar um segundo mecanismo.
 *
 * <p><b>Entrega sincrona, de proposito.</b> Um {@code @EventListener} comum roda
 * na transacao de quem publicou: a linha de auditoria confirma junto com a
 * mudanca de privilegio, ou nenhuma das duas confirma. Entrega assincrona
 * deixaria existir o instante em que o privilegio mudou e a trilha ainda nao
 * sabia — e e exatamente esse instante que alguem investigaria depois.
 *
 * <p>Só identificadores e um enum. Nome de papel, de pessoa ou lista de
 * permissoes nao entram: a trilha diz <b>o que</b> mudou, e nao repete o
 * conteudo do que mudou.
 */
public record PapelAlterado(UUID tenantId, UUID actorId, UUID roleId, Mudanca mudanca) {

    public enum Mudanca {
        CRIADO,
        ATUALIZADO,
        PERMISSOES_ALTERADAS,
        REMOVIDO,
        ATRIBUIDO,
        REVOGADO
    }
}
