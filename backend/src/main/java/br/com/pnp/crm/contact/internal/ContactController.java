package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.identity.api.UsuarioLookup;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contatos")
class ContactController {

    /** Teto de página. Sem ele, `?tamanho=1000000` vira negação de serviço. */
    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ContactRepository repository;
    private final UsuarioLookup users;
    private final Autorizacao autorizacao;

    ContactController(ContactRepository repository, UsuarioLookup users, Autorizacao autorizacao) {
        this.repository = repository;
        this.users = users;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<List<ContactDtos.ContatoResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {

        var page = repository.buscar(
                TenantContext.obrigatorio(),
                filtroDeResponsavel(Permissao.CONTACTS_READ),
                busca == null || busca.isBlank() ? null : busca.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA)));

        return ResponseEntity.ok(page.map(ContactController::paraResposta).getContent());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    ResponseEntity<ContactDtos.ContatoResponse> obter(@PathVariable UUID id) {
        autorizacao.exigir(Permissao.CONTACTS_READ);
        ContactEntity contato = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.CONTACTS_READ, contato.getOwnerUserId());
        return ResponseEntity.ok(paraResposta(contato));
    }

    @PostMapping
    @Transactional
    ResponseEntity<ContactDtos.ContatoResponse> criar(
            @Valid @RequestBody ContactDtos.ContatoRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        autorizacao.exigir(Permissao.CONTACTS_WRITE);

        UUID autorId = UUID.fromString(jwt.getSubject());
        ContactEntity contato = ContactEntity.novo(TenantContext.obrigatorio(), autorId);
        aplicar(contato, requisicao, autorId);
        // Quem só alcança o próprio não pode criar registro em nome de outro:
        // seria criar hoje o que não poderia ler amanhã.
        autorizacao.exigirSobreRegistro(Permissao.CONTACTS_WRITE, contato.getOwnerUserId());

        return ResponseEntity.ok(paraResposta(repository.save(contato)));
    }

    @PutMapping("/{id}")
    @Transactional
    ResponseEntity<ContactDtos.ContatoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ContactDtos.ContatoRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        autorizacao.exigir(Permissao.CONTACTS_WRITE);
        ContactEntity contato = carregar(id);
        // Duas verificações, não uma: o registro ANTES da alteração e o
        // responsável DEPOIS. Sem a primeira, alguém edita registro alheio;
        // sem a segunda, transfere o próprio registro para fora do seu alcance.
        autorizacao.exigirSobreRegistro(Permissao.CONTACTS_WRITE, contato.getOwnerUserId());
        aplicar(contato, requisicao, UUID.fromString(jwt.getSubject()));
        autorizacao.exigirSobreRegistro(Permissao.CONTACTS_WRITE, contato.getOwnerUserId());

        return ResponseEntity.ok(paraResposta(contato));
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigir(Permissao.CONTACTS_WRITE);
        ContactEntity contato = carregar(id);
        autorizacao.exigirSobreRegistro(Permissao.CONTACTS_WRITE, contato.getOwnerUserId());
        contato.excluir(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    private ContactEntity carregar(UUID id) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(id, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contato"));
    }

    /**
     * Devolve o id do usuário quando o alcance é próprio, ou {@code null} para
     * não filtrar. Também é o ponto que exige a permissão de leitura — assim
     * um endpoint novo que esqueça de chamar não passa despercebido: ele
     * simplesmente não compila sem escolher um filtro.
     */
    private UUID filtroDeResponsavel(Permissao permissao) {
        return autorizacao.alcanceDe(permissao) == Autorizacao.Alcance.PROPRIO
                ? autorizacao.usuarioCorrente()
                : null;
    }

    private void aplicar(ContactEntity contato, ContactDtos.ContatoRequest r, UUID autorId) {
        UUID responsavelId = autorizacao.responsavelPadrao(
                Permissao.CONTACTS_WRITE, r.responsavelId());
        // Decide o alcance antes de consultar a existência do usuário. Assim,
        // um perfil OWN recebe sempre 403 ao tentar atribuir a terceiro, sem
        // usar 403/404 como oráculo de usuários ativos no tenant.
        autorizacao.exigirSobreRegistro(Permissao.CONTACTS_WRITE, responsavelId);
        if (responsavelId != null
                && !users.existsActive(TenantContext.obrigatorio(), responsavelId)) {
            throw new RecursoNaoEncontradoException("Responsável");
        }
        contato.aplicar(r.tipo(), r.nome(), r.email(), r.telefone(), r.empresa(),
                r.observacoes(), responsavelId,
                autorId);
    }

    private static ContactDtos.ContatoResponse paraResposta(ContactEntity c) {
        return new ContactDtos.ContatoResponse(
                c.getId(), c.getKind(), c.getName(), c.getEmail(), c.getPhone(), c.getCompanyName(),
                c.getNotes(), c.getOwnerUserId(), c.getCreatedAt());
    }
}
