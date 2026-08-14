package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.organization.api.PapelAlterado;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Administração de papéis pelo próprio cliente.
 *
 * <p>Duas garantias atravessam todos os endpoints.
 *
 * <p><b>{@code organization.manage} no tenant, e mais nada além disso.</b>
 * Papel e atribuição são recursos coletivos: não têm responsável por registro, e
 * aceitar alcance próprio aqui transformaria uma concessão restrita em poder
 * sobre a empresa inteira.
 *
 * <p><b>A permissão de administrar não é permissão de se tornar qualquer
 * coisa.</b> Por cima da autorização, cada mutação passa pela
 * {@link GuardaDeConcessao}: ninguém concede o que não tem, nem sob alcance
 * mais amplo que o próprio, nem mexe em papel que concede além do seu.
 *
 * <p>Toda mudança é auditada como {@code ROLE_CHANGED} — papel é superfície de
 * privilégio, e alteração de privilégio sem rastro é o que impede reconstruir
 * um incidente depois. A trilha guarda identificadores, nunca nome de pessoa.
 *
 * <p><b>Recusa não vai para a trilha, e isso é deliberado.</b> Estes métodos
 * são transacionais, e a exceção de domínio marca a transação para rollback:
 * um {@code INSERT} de auditoria feito antes de lançar seria descartado junto,
 * deixando a aparência de rastro sem o rastro. Como recusa também não é
 * mudança de privilégio — nada aconteceu —, ela fica no log da aplicação. Só
 * gravar na trilha o que sobrevive ao commit mantém a trilha confiável.
 */
@RestController
@RequestMapping("/api/organizacao")
class PapelController {

    private final PapelRepository papeis;
    private final GuardaDeConcessao guarda;
    private final Autorizacao autorizacao;
    private final ApplicationEventPublisher eventos;

    PapelController(PapelRepository papeis, GuardaDeConcessao guarda,
                    Autorizacao autorizacao, ApplicationEventPublisher eventos) {
        this.papeis = papeis;
        this.guarda = guarda;
        this.autorizacao = autorizacao;
        this.eventos = eventos;
    }

    // ---------------------------------------------------------------- papéis

    @GetMapping("/papeis")
    @Transactional(readOnly = true)
    ResponseEntity<PapelDtos.CatalogoResponse> listar() {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        Set<String> noTenant = guarda.delegaveis(OrganizationAccess.ScopeType.TENANT);
        Set<String> naEquipe = guarda.delegaveis(OrganizationAccess.ScopeType.TEAM);

        List<PapelDtos.PermissaoResponse> catalogo = Arrays.stream(Permissao.values())
                .map(permissao -> new PapelDtos.PermissaoResponse(permissao.codigo(),
                        noTenant.contains(permissao.codigo()),
                        naEquipe.contains(permissao.codigo()),
                        possuidas().contains(permissao.codigo())))
                .toList();

        List<PapelDtos.PapelResponse> resposta = papeis.listar().stream()
                .map(this::resposta)
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new PapelDtos.CatalogoResponse(resposta, catalogo));
    }

    @PostMapping("/papeis")
    @Transactional
    ResponseEntity<PapelDtos.PapelResponse> criar(
            @Valid @RequestBody PapelDtos.CriarPapelRequest requisicao) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);
        exigirPoderDefinir(requisicao.permissoes());
        exigirCodigoLivre(requisicao.codigo());

        UUID autor = autorizacao.usuarioCorrente();
        UUID id = papeis.criar(requisicao.codigo(), requisicao.nome(),
                requisicao.descricao(), autor);
        papeis.definirPermissoes(id, requisicao.permissoes(), autor);

        registrar(id, autor, PapelAlterado.Mudanca.CRIADO);
        return ResponseEntity.ok(resposta(papeis.obrigatorio(id)));
    }

    /**
     * Instala a base comercial sem sobrescrever o que o cliente já editou.
     * Repetir a chamada é seguro: códigos existentes são preservados e apenas
     * papéis ausentes são criados.
     */
    @PostMapping("/papeis/presets/comercial")
    @Transactional
    ResponseEntity<PapelDtos.PresetComercialResponse> aplicarPresetComercial() {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);
        PresetComercial.papeis().forEach(definicao ->
                exigirPoderDefinir(definicao.permissoes()));

        Map<String, PapelRepository.Papel> porCodigo = new LinkedHashMap<>();
        papeis.listar().forEach(papel -> porCodigo.put(papel.codigo(), papel));

        UUID autor = autorizacao.usuarioCorrente();
        int criados = 0;
        for (PresetComercial.Definicao definicao : PresetComercial.papeis()) {
            if (porCodigo.containsKey(definicao.codigo())) continue;

            UUID id = papeis.criar(definicao.codigo(), definicao.nome(),
                    definicao.descricao(), autor);
            papeis.definirPermissoes(id, definicao.permissoes(), autor);
            registrar(id, autor, PapelAlterado.Mudanca.CRIADO);
            porCodigo.put(definicao.codigo(), papeis.obrigatorio(id));
            criados++;
        }

        List<PapelDtos.PapelResponse> papeisDoPreset = PresetComercial.papeis().stream()
                .map(definicao -> resposta(porCodigo.get(definicao.codigo())))
                .toList();
        return ResponseEntity.ok(new PapelDtos.PresetComercialResponse(
                criados, papeisDoPreset));
    }

    @PutMapping("/papeis/{papelId}")
    @Transactional
    ResponseEntity<PapelDtos.PapelResponse> atualizar(
            @PathVariable UUID papelId,
            @Valid @RequestBody PapelDtos.AtualizarPapelRequest requisicao) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        PapelRepository.Papel papel = exigirEditavel(papelId);
        UUID autor = autorizacao.usuarioCorrente();
        papeis.atualizar(papelId, requisicao.nome(), requisicao.descricao(),
                requisicao.ativo(), autor);

        registrar(papelId, autor, PapelAlterado.Mudanca.ATUALIZADO);
        return ResponseEntity.ok(resposta(papeis.obrigatorio(papel.id())));
    }

    @PutMapping("/papeis/{papelId}/permissoes")
    @Transactional
    ResponseEntity<PapelDtos.PapelResponse> definirPermissoes(
            @PathVariable UUID papelId,
            @Valid @RequestBody PapelDtos.PermissoesRequest requisicao) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        exigirEditavel(papelId);
        exigirPoderDefinir(requisicao.permissoes());

        UUID autor = autorizacao.usuarioCorrente();
        papeis.definirPermissoes(papelId, requisicao.permissoes(), autor);

        registrar(papelId, autor, PapelAlterado.Mudanca.PERMISSOES_ALTERADAS);
        return ResponseEntity.ok(resposta(papeis.obrigatorio(papelId)));
    }

    @DeleteMapping("/papeis/{papelId}")
    @Transactional
    ResponseEntity<Void> remover(@PathVariable UUID papelId) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        PapelRepository.Papel papel = exigirEditavel(papelId);
        if (papel.atribuicoes() > 0) {
            throw new PapelEmUsoException(papel.atribuicoes());
        }

        UUID autor = autorizacao.usuarioCorrente();
        papeis.remover(papelId, autor);

        registrar(papelId, autor, PapelAlterado.Mudanca.REMOVIDO);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------- atribuições

    @GetMapping("/membros")
    @Transactional(readOnly = true)
    ResponseEntity<List<PapelDtos.MembroResponse>> membros() {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        List<PapelDtos.MembroResponse> resposta = papeis.membros().stream()
                .map(membro -> new PapelDtos.MembroResponse(
                        membro.membershipId(), membro.usuarioId(), membro.login(), membro.nome(),
                        membro.atribuicoes().stream()
                                .map(item -> new PapelDtos.AtribuicaoResponse(
                                        item.id(), item.papelId(), item.papelCodigo(),
                                        item.papelNome(), item.alcance()))
                                .toList()))
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(resposta);
    }

    @PostMapping("/membros/{membershipId}/papeis")
    @Transactional
    ResponseEntity<Void> atribuir(@PathVariable UUID membershipId,
                                  @Valid @RequestBody PapelDtos.AtribuirPapelRequest requisicao) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        papeis.membroObrigatorio(membershipId);
        PapelRepository.Papel papel = papeis.obrigatorio(requisicao.papelId());
        OrganizationAccess.ScopeType alcance =
                OrganizationAccess.ScopeType.valueOf(requisicao.alcance());

        // A guarda incide sobre o que o papel concede, não sobre o papel: quem
        // atribui precisa ter, ele próprio, cada permissão que está passando
        // adiante, sob alcance que a contenha.
        guarda.exigirPoderDelegar(papel.permissoes(), alcance);

        UUID autor = autorizacao.usuarioCorrente();
        papeis.atribuir(membershipId, papel.id(), requisicao.alcance(), autor);

        registrar(papel.id(), autor, PapelAlterado.Mudanca.ATRIBUIDO);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/membros/{membershipId}/papeis/{atribuicaoId}")
    @Transactional
    ResponseEntity<Void> revogar(@PathVariable UUID membershipId,
                                 @PathVariable UUID atribuicaoId) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);

        PapelRepository.Membro membro = papeis.membroObrigatorio(membershipId);
        PapelRepository.Atribuicao atribuicao = membro.atribuicoes().stream()
                .filter(item -> item.id().equals(atribuicaoId))
                .findFirst()
                .orElseThrow(() ->
                        new br.com.pnp.crm.shared.api.RecursoNaoEncontradoException("Atribuição"));

        PapelRepository.Papel papel = papeis.obrigatorio(atribuicao.papelId());
        UUID autor = autorizacao.usuarioCorrente();

        // Revogar não exige poder delegar — tirar acesso nunca é escalonamento.
        // Exige, sim, que sobre alguém capaz de administrar.
        if (papel.sistema() && papeis.atribuicoesDeSistemaVivas() <= 1) {
            throw new UltimoProprietarioException();
        }

        papeis.revogar(membershipId, atribuicaoId, autor);

        registrar(papel.id(), autor, PapelAlterado.Mudanca.REVOGADO);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------- apoio

    /**
     * Papel existente que o autor pode mexer.
     *
     * <p>As duas condições são diferentes e ambas necessárias: papel de sistema
     * é imutável para todos, e papel comum só é editável por quem já possui
     * tudo o que ele concede.
     */
    private void exigirCodigoLivre(String codigo) {
        boolean ocupado = papeis.listar().stream()
                .anyMatch(papel -> papel.codigo().equals(codigo));
        if (ocupado) {
            throw new CodigoDePapelEmUsoException(codigo);
        }
    }

    /**
     * Definir o conteúdo de um papel exige possuir cada permissão — e só isso.
     *
     * <p>Papel é uma lista de permissões e <b>não concede nada até ser
     * atribuído</b>. O alcance entra na atribuição, e é lá que
     * {@link GuardaDeConcessao#exigirPoderDelegar} confere se o autor pode
     * entregar aquele recorte. Exigir alcance de tenant já na definição
     * impediria um administrador de alcance próprio de montar um papel que ele
     * poderia legitimamente atribuir em alcance próprio.
     *
     * <p>E não abre escalonamento: quem só possui a permissão sob alcance
     * próprio continua só conseguindo atribuir o papel sob alcance próprio.
     *
     * <p>Esta é a mesma pergunta que {@code gerenciavel} responde na listagem.
     * Quando as duas divergiram, a tela oferecia um botão de editar que o
     * salvamento recusava.
     */
    private void exigirPoderDefinir(java.util.Collection<String> permissoes) {
        guarda.exigirPoderDelegar(permissoes, OrganizationAccess.ScopeType.OWN);
    }

    private PapelRepository.Papel exigirEditavel(UUID papelId) {
        PapelRepository.Papel papel = papeis.obrigatorio(papelId);
        if (papel.sistema()) {
            throw new PapelDeSistemaException();
        }
        guarda.exigirPoderGerenciar(papel.permissoes());
        return papel;
    }

    private PapelDtos.PapelResponse resposta(PapelRepository.Papel papel) {
        // Espelha exatamente a decisão de exigirEditavel. Se divergirem, a tela
        // oferece um botão que o backend recusa — ou esconde um que funciona.
        boolean gerenciavel = !papel.sistema() && possuidas().containsAll(papel.permissoes());
        return new PapelDtos.PapelResponse(papel.id(), papel.codigo(), papel.nome(),
                papel.descricao(), papel.sistema(), papel.ativo(), papel.permissoes(),
                gerenciavel, papel.atribuicoes());
    }

    /**
     * Permissões que o autor exerce em algum alcance decidível.
     *
     * <p>É o mesmo conjunto que {@link GuardaDeConcessao#exigirPoderGerenciar}
     * consulta: delegável sob alcance próprio equivale a "eu tenho isto", já que
     * TENANT contém OWN e os demais alcances não decidem nada hoje.
     */
    private Set<String> possuidas() {
        return guarda.delegaveis(OrganizationAccess.ScopeType.OWN);
    }

    /**
     * Publica a mudança para quem registra auditoria.
     *
     * <p>Evento, e não chamada direta a {@code AuditTrail}: o módulo de
     * auditoria já depende de {@code organization.api}, e chamá-lo daqui
     * fecharia um ciclo entre os dois. A entrega é síncrona, então a linha da
     * trilha confirma na mesma transação da mudança.
     */
    private void registrar(UUID papelId, UUID autor, PapelAlterado.Mudanca mudanca) {
        eventos.publishEvent(new PapelAlterado(
                TenantContext.obrigatorio(), autor, papelId, mudanca));
    }
}
