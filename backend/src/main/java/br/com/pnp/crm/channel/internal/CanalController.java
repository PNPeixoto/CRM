package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
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

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChannelConnectionRepository conexoes;
    private final CredenciaisDeCanal credenciais;

    private final Autorizacao autorizacao;
    private final AuditTrail audit;
    /**
     * Opcional por construção: o adaptador só nasce com
     * {@code app.providers.evolution.enabled=true}. Uma dependência rígida
     * impediria o CRM de subir em produção, onde a ponte experimental é
     * deliberadamente desligada.
     */
    private final ObjectProvider<EvolutionAdapter> evolution;

    CanalController(ChannelConnectionRepository conexoes, CredenciaisDeCanal credenciais,
                    Autorizacao autorizacao, AuditTrail audit,
                    ObjectProvider<EvolutionAdapter> evolution) {
        this.conexoes = conexoes;
        this.credenciais = credenciais;
        this.autorizacao = autorizacao;
        this.audit = audit;
        this.evolution = evolution;
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
        boolean credencialAlterada = guardarCredenciais(conexao.getId(), requisicao, true);
        auditarConfiguracao(conexao.getId(), autorId, AuditTrail.Motivo.CHANNEL_CREATED);
        if (credencialAlterada) auditarCredencial(conexao.getId(), autorId);

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
        UUID autorId = UUID.fromString(jwt.getSubject());
        boolean credencialAlterada = guardarCredenciais(id, requisicao, false);
        auditarConfiguracao(id, autorId, AuditTrail.Motivo.CHANNEL_UPDATED);
        if (credencialAlterada) auditarCredencial(id, autorId);
        return ResponseEntity.ok(paraResposta(conexao));
    }

    @PostMapping("/{id}/ativacao")
    @Transactional
    ResponseEntity<CanalResponse> alternarAtivacao(@PathVariable UUID id,
                                                   @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.CHANNELS_WRITE);
        ChannelConnectionEntity conexao = carregar(id);
        UUID autorId = UUID.fromString(jwt.getSubject());
        conexao.alternarAtivacao(autorId);
        auditarConfiguracao(id, autorId, AuditTrail.Motivo.CHANNEL_ACTIVATION_CHANGED);
        return ResponseEntity.ok(paraResposta(conexao));
    }

    /**
     * Material para parear um canal Evolution.
     *
     * <p>Existe porque parear era, até aqui, impossível pela aplicação: era
     * preciso chamar a Evolution na mão, com a API key do servidor. Isso
     * obrigava a distribuir a chave de administração do provedor para alguém
     * conectar um número — a credencial mais ampla do laboratório entregue
     * para a tarefa mais corriqueira.
     *
     * <p><b>Nunca reconecta uma sessão saudável.</b> Se a instância já está
     * {@code open}, devolve o estado e não pede QR novo. Pedir reinicia o
     * pareamento no provedor: um clique acidental derrubaria um WhatsApp que
     * estava atendendo.
     *
     * <p>A resposta carrega credencial de sessão e por isso é {@code no-store}.
     * O conteúdo não é auditado nem registrado — a auditoria guarda que houve
     * pareamento, quem pediu e quando, jamais o material.
     */
    @PostMapping("/{id}/pareamento")
    @Transactional
    ResponseEntity<PareamentoResponse> parear(@PathVariable UUID id,
                                              @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.CHANNELS_WRITE);
        ChannelConnectionEntity conexao = carregar(id);

        if (conexao.getKind() != TipoCanal.WHATSAPP_EVOLUTION) {
            throw new PareamentoIndisponivelException(
                    "Somente o canal WhatsApp Evolution é pareado por QR.");
        }
        if (!conexao.isActive()) {
            throw new PareamentoIndisponivelException(
                    "Ative o canal antes de parear.");
        }
        EvolutionAdapter adapter = evolution.getIfAvailable();
        if (adapter == null) {
            throw new PareamentoIndisponivelException(
                    "O adaptador Evolution não está habilitado neste servidor.");
        }

        adapter.garantirInstancia(id);
        String estado = adapter.obterEstado(id).estadoDaConexao();
        if ("open".equalsIgnoreCase(estado)) {
            return semCache(new PareamentoResponse(estado, null, null, 0));
        }

        EvolutionAdapter.Pareamento pareamento = adapter.iniciarPareamento(id);
        // Parear estabelece uma sessão capaz de enviar como aquele número:
        // é evento de credencial, e não de mera configuração.
        auditarCredencial(id, UUID.fromString(jwt.getSubject()));

        return semCache(new PareamentoResponse(
                estado,
                vazioParaNulo(pareamento.qrCodeBase64()),
                vazioParaNulo(pareamento.codigoDeParear()),
                pareamento.tentativa()));
    }

    private ResponseEntity<PareamentoResponse> semCache(PareamentoResponse corpo) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(corpo);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
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
    private boolean guardarCredenciais(UUID conexaoId, CanalRequest requisicao,
                                       boolean novaConexao) {
        UUID tenantId = TenantContext.obrigatorio();
        boolean alterada = false;

        if (requisicao.tipo() == TipoCanal.TELEGRAM) {
            if (preenchido(requisicao.token())) {
                credenciais.guardar(tenantId, conexaoId,
                        TipoCredencial.TELEGRAM_BOT_TOKEN, requisicao.token());
                alterada = true;
            }
            if (preenchido(requisicao.segredoWebhook())) {
                credenciais.guardar(tenantId, conexaoId,
                        TipoCredencial.TELEGRAM_WEBHOOK_SECRET, requisicao.segredoWebhook());
                alterada = true;
            } else if (novaConexao) {
                credenciais.guardar(tenantId, conexaoId,
                        TipoCredencial.TELEGRAM_WEBHOOK_SECRET, gerarSegredoWebhook());
                alterada = true;
            }
        } else if (requisicao.tipo() == TipoCanal.WHATSAPP_EVOLUTION && novaConexao) {
            credenciais.guardar(tenantId, conexaoId,
                    TipoCredencial.EVOLUTION_WEBHOOK_SECRET, gerarSegredoWebhook());
            alterada = true;
        }
        return alterada;
    }

    private void auditarConfiguracao(UUID alvoId, UUID autorId, AuditTrail.Motivo motivo) {
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.SENSITIVE_CONFIGURATION_CHANGED,
                AuditTrail.Ator.humano(autorId),
                AuditTrail.Escopo.tenant(TenantContext.obrigatorio()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.CHANNEL_CONNECTION, alvoId),
                AuditTrail.Resultado.SUCCEEDED, motivo));
    }

    private void auditarCredencial(UUID alvoId, UUID autorId) {
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.CREDENTIAL_CHANGED,
                AuditTrail.Ator.humano(autorId),
                AuditTrail.Escopo.tenant(TenantContext.obrigatorio()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.CHANNEL_CONNECTION, alvoId),
                AuditTrail.Resultado.SUCCEEDED, AuditTrail.Motivo.CHANNEL_CREDENTIAL_CHANGED));
    }

    /**
     * O segredo do webhook e interno: gerar no servidor evita copia manual,
     * reutilizacao de senha e exposicao no navegador. O alfabeto Base64 URL
     * sem padding e aceito pelo cabecalho secreto da Telegram Bot API.
     */
    private String gerarSegredoWebhook() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private ChannelConnectionEntity carregar(UUID id) {
        return conexoes.findByIdAndTenantIdAndDeletedAtIsNull(id, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Canal"));
    }

    private CanalResponse paraResposta(ChannelConnectionEntity c) {
        TipoCredencial token = c.getKind() == TipoCanal.TELEGRAM
                ? TipoCredencial.TELEGRAM_BOT_TOKEN : null;
        TipoCredencial segredo = c.getKind() == TipoCanal.WHATSAPP_EVOLUTION
                ? TipoCredencial.EVOLUTION_WEBHOOK_SECRET
                : c.getKind() == TipoCanal.TELEGRAM
                    ? TipoCredencial.TELEGRAM_WEBHOOK_SECRET : null;
        return new CanalResponse(
                c.getId(), c.getKind().name(), c.getName(), c.getExternalAccountId(), c.isActive(),
                token == null || credenciais.recuperar(c.getId(), token).isPresent(),
                segredo == null || credenciais.recuperar(c.getId(), segredo).isPresent(),
                c.getRemoteStatus(), c.getRemotePendingCount(),
                c.getLastReconciledAt(), c.getLastRemoteErrorAt());
    }

    record CanalRequest(
            @NotNull(message = "Informe o tipo do canal.") TipoCanal tipo,
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @Size(max = 120) String identificadorExterno,
            @Size(max = 300) String token,
            @Size(max = 300) String segredoWebhook) {
    }

    /**
     * Estado do pareamento.
     *
     * <p>{@code qrCodeBase64} e {@code codigoDeParear} vêm nulos quando a
     * sessão já está aberta — não há o que parear, e devolver material novo
     * convidaria a derrubar a conexão em uso.
     */
    record PareamentoResponse(String estado, String qrCodeBase64,
                              String codigoDeParear, int tentativa) {
        @Override
        public String toString() {
            return "PareamentoResponse[estado=" + estado + ", conteudo omitido]";
        }
    }

    /** Sem os segredos — apenas a informação de que existem. */
    record CanalResponse(
            UUID id, String tipo, String nome, String identificadorExterno, boolean ativo,
            boolean temToken, boolean temSegredoWebhook,
            String estadoRemoto, Integer pendenciasRemotas,
            Instant ultimaReconciliacaoEm, Instant ultimaFalhaRemotaEm) {
    }
}
