package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.identity.api.UsuarioLookup;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/contatos")
class ContactController {

    /** Teto de página. Sem ele, `?tamanho=1000000` vira negação de serviço. */
    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ContactRepository repository;
    private final UsuarioLookup users;
    private final Autorizacao autorizacao;
    private final ContactCreationService creation;

    ContactController(ContactRepository repository, UsuarioLookup users, Autorizacao autorizacao,
                      ContactCreationService creation) {
        this.repository = repository;
        this.users = users;
        this.autorizacao = autorizacao;
        this.creation = creation;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<List<ContactDtos.ContatoResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho,
            @RequestParam(defaultValue = "nome") String ordenarPor) {

        validarConsulta(busca, pagina, tamanho, ordenarPor);

        Autorizacao.Recorte recorte = autorizacao.recorteDe(Permissao.CONTACTS_READ);
        var page = repository.buscar(
                TenantContext.obrigatorio(),
                recorte.todoOTenant(), recorte.responsaveis(),
                busca == null || busca.isBlank() ? null : busca.trim(),
                PageRequest.of(pagina, tamanho));

        List<ContactEntity> contatos = page.getContent();
        Map<UUID, UsuarioLookup.UsuarioReference> responsaveis = users.findKnown(
                TenantContext.obrigatorio(), contatos.stream()
                        .map(ContactEntity::getOwnerUserId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList());

        return ResponseEntity.ok(contatos.stream()
                .map(contato -> paraResposta(
                        contato, responsaveis.get(contato.getOwnerUserId())))
                .toList());
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
    ResponseEntity<ContactDtos.ContatoResponse> criar(
            @Valid @RequestBody ContactDtos.ContatoRequest requisicao,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(paraResposta(creation.criar(
                requisicao, UUID.fromString(jwt.getSubject()), idempotencyKey)));
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


    private static void validarConsulta(String busca, int pagina, int tamanho, String ordenarPor) {
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            throw new RequisicaoInvalidaException(
                    "Página e tamanho devem respeitar os limites do servidor.");
        }
        if (!"nome".equals(ordenarPor)) {
            throw new RequisicaoInvalidaException("Ordenação não permitida.");
        }
        if (busca != null && busca.length() > 100) {
            throw new RequisicaoInvalidaException("Filtro de busca longo demais.");
        }
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

    private ContactDtos.ContatoResponse paraResposta(ContactEntity c) {
        UsuarioLookup.UsuarioReference responsavel = c.getOwnerUserId() == null
                ? null
                : users.findKnown(TenantContext.obrigatorio(), List.of(c.getOwnerUserId()))
                        .get(c.getOwnerUserId());
        return paraResposta(c, responsavel);
    }

    private static ContactDtos.ContatoResponse paraResposta(
            ContactEntity c, UsuarioLookup.UsuarioReference responsavel) {
        return new ContactDtos.ContatoResponse(
                c.getId(), c.getKind(), c.getName(), c.getEmail(), c.getPhone(), c.getCompanyName(),
                c.getNotes(), c.getOwnerUserId(),
                responsavel == null ? null : responsavel.login(),
                responsavel == null ? null : responsavel.nomeCompleto(),
                c.getCreatedAt());
    }
}
