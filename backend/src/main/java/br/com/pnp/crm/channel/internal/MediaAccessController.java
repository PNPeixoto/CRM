package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Entrega somente midia liberada, autenticada e com URL HMAC de cinco minutos. */
@RestController
@RequestMapping("/api/midias")
class MediaAccessController {

    private final EntityManager entityManager;
    private final AssinaturaDeMidia assinatura;
    private final QuarentenaDeMidia storage;
    private final Autorizacao autorizacao;

    MediaAccessController(EntityManager entityManager, AssinaturaDeMidia assinatura,
                          QuarentenaDeMidia storage, Autorizacao autorizacao) {
        this.entityManager = entityManager;
        this.assinatura = assinatura;
        this.storage = storage;
        this.autorizacao = autorizacao;
    }

    @PostMapping("/{id}/url")
    @Transactional(readOnly = true)
    ResponseEntity<UrlDeMidia> criarUrl(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.CONVERSATIONS_READ);
        Midia midia = carregarDisponivel(id);
        long expiraEm = Instant.now().plusSeconds(300).getEpochSecond();
        String sig = assinatura.assinar(TenantContext.obrigatorio(), id, expiraEm);
        return ResponseEntity.ok(new UrlDeMidia(
                "/api/midias/" + id + "/conteudo?exp=" + expiraEm + "&sig=" + sig,
                Instant.ofEpochSecond(expiraEm)));
    }

    @GetMapping("/{id}/conteudo")
    @Transactional(readOnly = true)
    ResponseEntity<Resource> baixar(@PathVariable UUID id,
                                    @RequestParam long exp,
                                    @RequestParam String sig) {
        autorizacao.exigirNoTenant(Permissao.CONVERSATIONS_READ);
        UUID tenantId = TenantContext.obrigatorio();
        if (!assinatura.conferir(tenantId, id, exp, sig)) {
            return ResponseEntity.status(403).build();
        }
        Midia midia = carregarDisponivel(id);
        Resource recurso = new FileSystemResource(storage.resolver(midia.storageKey()));
        if (!recurso.exists()) throw new RecursoNaoEncontradoException("Midia");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(midia.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"media-" + id + extensao(midia.tipo()) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(midia.tamanho())
                .body(recurso);
    }

    private Midia carregarDisponivel(UUID id) {
        List<Tuple> linhas = entityManager.createNativeQuery("""
                SELECT storage_key, detected_mime_type, byte_size
                  FROM channel_media
                 WHERE id = :id AND tenant_id = :tenant
                   AND status = 'AVAILABLE' AND deleted_at IS NULL
                """, Tuple.class)
                .setParameter("id", id)
                .setParameter("tenant", TenantContext.obrigatorio())
                .getResultList();
        if (linhas.isEmpty()) throw new RecursoNaoEncontradoException("Midia");
        Tuple linha = linhas.getFirst();
        return new Midia(linha.get(0, String.class), linha.get(1, String.class),
                linha.get(2, Long.class));
    }

    private String extensao(String tipo) {
        return switch (tipo) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "audio/ogg" -> ".ogg";
            case "video/mp4" -> ".mp4";
            default -> ".bin";
        };
    }

    record UrlDeMidia(String url, Instant expiraEm) {
    }

    private record Midia(String storageKey, String tipo, long tamanho) {
    }
}
