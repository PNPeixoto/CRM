package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Canais e números conectados.
 *
 * <p>Fecha a lacuna registrada no `SETUP-TELEGRAM.md`: até aqui, conexão e
 * credencial só existiam por SQL na mão.
 */
@RestController
@RequestMapping("/api/canais")
class CanalController {

    private final ChannelConnectionRepository conexoes;
    private final CredenciaisDeCanal credenciais;

    private final Autorizacao autorizacao;

    CanalController(ChannelConnectionRepository conexoes, CredenciaisDeCanal credenciais,
                    Autorizacao autorizacao) {
        this.conexoes = conexoes;
        this.credenciais = credenciais;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<List<CanalResponse>> listar() {
        // Canal é recurso do tenant, não de um responsável: não há alcance
        // próprio a aplicar, só a permissão.
        autorizacao.exigirNoTenant(Permissao.CHANNELS_READ);
        return ResponseEntity.ok(
                conexoes.findByTenantIdAndDeletedAtIsNullOrderByName(TenantContext.obrigatorio())
                        .stream().map(this::paraResposta).toList());
    }

    @PostMapping
    @Transactional
    ResponseEntity<CanalResponse> criar(@Valid @RequestBody CanalRequest requisicao,
                                        @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.CHANNELS_WRITE);
        UUID autorId = UUID.fromString(jwt.getSubject());
        ChannelConnectionEntity conexao = ChannelConnectionEntity.nova(
                TenantContext.obrigatorio(), requisicao.tipo(), requisicao.nome(),
                requisicao.identificadorExterno(), autorId);

        conexoes.saveAndFlush(conexao);
        guardarCredenciais(conexao.getId(), requisicao);

        return ResponseEntity.ok(paraResposta(conexao));
    }

    @PutMapping("/{id}")
    @Transactional
    ResponseEntity<CanalResponse> atualizar(@PathVariable UUID id,
                                            @Valid @RequestBody CanalRequest requisicao,
                                            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.CHANNELS_WRITE);
        ChannelConnectionEntity conexao = carregar(id);
        conexao.renomear(requisicao.nome(), requisicao.identificadorExterno(),
                UUID.fromString(jwt.getSubject()));
        guardarCredenciais(id, requisicao);
        return ResponseEntity.ok(paraResposta(conexao));
    }

    @PostMapping("/{id}/ativacao")
    @Transactional
    ResponseEntity<CanalResponse> alternarAtivacao(@PathVariable UUID id,
                                                   @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.CHANNELS_WRITE);
        ChannelConnectionEntity conexao = carregar(id);
        conexao.alternarAtivacao(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(paraResposta(conexao));
    }

    /**
     * Credenciais só entram; nunca saem.
     *
     * <p>Não existe endpoint que devolva token de bot ou segredo de webhook, e
     * a resposta informa apenas <b>se</b> há credencial cadastrada. Um CRM que
     * exibe o token na tela de configuração o entrega a qualquer XSS, a
     * qualquer captura de tela e a qualquer pessoa que passe atrás do
     * atendente.
     */
    private void guardarCredenciais(UUID conexaoId, CanalRequest requisicao) {
        UUID tenantId = TenantContext.obrigatorio();

        if (preenchido(requisicao.token())) {
            credenciais.guardar(tenantId, conexaoId,
                    TipoCredencial.TELEGRAM_BOT_TOKEN, requisicao.token());
        }
        if (preenchido(requisicao.segredoWebhook())) {
            credenciais.guardar(tenantId, conexaoId,
                    TipoCredencial.TELEGRAM_WEBHOOK_SECRET, requisicao.segredoWebhook());
        }
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private ChannelConnectionEntity carregar(UUID id) {
        return conexoes.findByIdAndTenantIdAndDeletedAtIsNull(id, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Canal"));
    }

    private CanalResponse paraResposta(ChannelConnectionEntity c) {
        return new CanalResponse(
                c.getId(), c.getKind().name(), c.getName(), c.getExternalAccountId(), c.isActive(),
                credenciais.recuperar(c.getId(), TipoCredencial.TELEGRAM_BOT_TOKEN).isPresent(),
                credenciais.recuperar(c.getId(), TipoCredencial.TELEGRAM_WEBHOOK_SECRET).isPresent());
    }

    record CanalRequest(
            @NotNull(message = "Informe o tipo do canal.") TipoCanal tipo,
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @Size(max = 120) String identificadorExterno,
            @Size(max = 300) String token,
            @Size(max = 300) String segredoWebhook) {
    }

    /** Sem os segredos — apenas a informação de que existem. */
    record CanalResponse(
            UUID id, String tipo, String nome, String identificadorExterno, boolean ativo,
            boolean temToken, boolean temSegredoWebhook) {
    }
}
