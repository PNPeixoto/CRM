package br.com.pnp.crm.contact.internal;

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

    ContactController(ContactRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<List<ContactDtos.ContatoResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {

        var page = repository.buscar(
                TenantContext.obrigatorio(),
                busca == null || busca.isBlank() ? null : busca.trim(),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA)));

        return ResponseEntity.ok(page.map(ContactController::paraResposta).getContent());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    ResponseEntity<ContactDtos.ContatoResponse> obter(@PathVariable UUID id) {
        return ResponseEntity.ok(paraResposta(carregar(id)));
    }

    @PostMapping
    @Transactional
    ResponseEntity<ContactDtos.ContatoResponse> criar(
            @Valid @RequestBody ContactDtos.ContatoRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        UUID autorId = UUID.fromString(jwt.getSubject());
        ContactEntity contato = ContactEntity.novo(TenantContext.obrigatorio(), autorId);
        aplicar(contato, requisicao, autorId);

        return ResponseEntity.ok(paraResposta(repository.save(contato)));
    }

    @PutMapping("/{id}")
    @Transactional
    ResponseEntity<ContactDtos.ContatoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ContactDtos.ContatoRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {

        ContactEntity contato = carregar(id);
        aplicar(contato, requisicao, UUID.fromString(jwt.getSubject()));

        return ResponseEntity.ok(paraResposta(contato));
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        carregar(id).excluir(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    private ContactEntity carregar(UUID id) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(id, TenantContext.obrigatorio())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contato"));
    }

    private void aplicar(ContactEntity contato, ContactDtos.ContatoRequest r, UUID autorId) {
        contato.aplicar(r.nome(), r.email(), r.telefone(), r.empresa(),
                r.observacoes(), r.responsavelId(), autorId);
    }

    private static ContactDtos.ContatoResponse paraResposta(ContactEntity c) {
        return new ContactDtos.ContatoResponse(
                c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCompanyName(),
                c.getNotes(), c.getOwnerUserId(), c.getCreatedAt());
    }
}
