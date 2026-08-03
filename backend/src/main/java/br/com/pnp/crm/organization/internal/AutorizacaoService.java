package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decide autorização a partir do membership vigente do usuário autenticado.
 *
 * <p><b>Escopo de requisição, com cache.</b> Resolver membership, papéis e
 * permissões custa consultas com junções temporais; um endpoint que verifica
 * ação e depois registro faria o mesmo trabalho duas vezes. O cache vive
 * apenas durante a requisição — nunca entre requisições, porque revogar um
 * papel precisa ter efeito na chamada seguinte, não quando um cache expirar.
 */
@Service
@RequestScope
class AutorizacaoService implements Autorizacao {

    private static final Logger log = LoggerFactory.getLogger(AutorizacaoService.class);

    private final OrganizationAccess acessos;

    /** Memoização por permissão dentro da requisição. */
    private final Map<Permissao, Alcance> resolvidas = new HashMap<>();

    private Map<String, OrganizationAccess.ScopeType> escopoPorPermissao;

    AutorizacaoService(OrganizationAccess acessos) {
        this.acessos = acessos;
    }

    @Override
    public void exigir(Permissao permissao) {
        alcanceDe(permissao);
    }

    @Override
    public void exigirMembershipVigente() {
        try {
            carregarEscopos();
        } catch (AcessoNegadoException e) {
            log.warn("Acesso negado. motivo=membership ausente ou sem vigência usuario={}",
                    usuarioCorrente());
            throw e;
        }
    }

    @Override
    public void exigirNoTenant(Permissao permissao) {
        if (alcanceDe(permissao) != Alcance.TENANT) {
            throw negar(permissao, "recurso exige alcance de tenant");
        }
    }

    @Override
    public Alcance alcanceDe(Permissao permissao) {
        return resolvidas.computeIfAbsent(permissao, this::resolver);
    }

    @Override
    public void exigirSobreRegistro(Permissao permissao, UUID responsavelId) {
        if (alcanceDe(permissao) == Alcance.TENANT) {
            return;
        }
        // Alcance próprio: só o responsável age. Registro sem responsável não
        // é de ninguém — negar é a leitura segura.
        if (responsavelId == null || !responsavelId.equals(usuarioCorrente())) {
            throw negar(permissao, "registro fora do alcance próprio");
        }
    }

    @Override
    public UUID usuarioCorrente() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new AcessoNegadoException();
    }

    private Alcance resolver(Permissao permissao) {
        OrganizationAccess.ScopeType escopo = carregarEscopos().get(permissao.codigo());
        if (escopo == null) {
            throw negar(permissao, "permissão ausente no membership vigente");
        }
        return switch (escopo) {
            case TENANT -> Alcance.TENANT;
            case OWN -> Alcance.PROPRIO;
            // UNIT, TEAM e NETWORK não decidem sobre registro de domínio
            // enquanto nenhuma tabela declarar unidade — ADR-0008. Falha
            // fechada: conceder o tenant inteiro aqui seria ampliar
            // silenciosamente quem foi restrito de propósito.
            default -> throw negar(permissao, "escopo sem decisão de domínio: " + escopo);
        };
    }

    /**
     * Carrega uma vez por requisição o alcance de cada permissão concedida.
     *
     * <p>Lê o alcance por permissão, e não o conjunto achatado de permissões do
     * contexto de tenant: naquele conjunto toda concessão parece vir do tenant
     * inteiro, e alcance próprio deixaria de existir na prática.
     *
     * <p>A consulta lança {@code AcessoNegadoException} quando não há
     * membership ACTIVE e vigente — é o que faz um usuário desligado perder
     * acesso na requisição seguinte, sem depender de expirar token.
     */
    private Map<String, OrganizationAccess.ScopeType> carregarEscopos() {
        if (escopoPorPermissao == null) {
            escopoPorPermissao =
                    acessos.permissionScopes(TenantContext.obrigatorio(), usuarioCorrente());
        }
        return escopoPorPermissao;
    }

    /**
     * Registra a negação e devolve a exceção para quem chamou lançar.
     *
     * <p>O log traz permissão e usuário, e <b>não</b> traz o identificador do
     * registro alvo. Anotar o id de cada recurso negado transformaria o log em
     * um inventário do que existe — que é justamente o que uma tentativa de
     * IDOR quer descobrir.
     */
    private AcessoNegadoException negar(Permissao permissao, String motivo) {
        log.warn("Acesso negado. permissao={} motivo={} usuario={}",
                permissao.codigo(), motivo, usuarioCorrente());
        return new AcessoNegadoException();
    }
}
