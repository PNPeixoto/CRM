package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.identity.api.UsuarioLookup;
import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Caso de uso pequeno da fatia vertical: autorização, replay e persistência. */
@Service
class ContactCreationService {

    private final ContactRepository repository;
    private final UsuarioLookup users;
    private final Autorizacao autorizacao;
    private final EntityManager entityManager;

    ContactCreationService(ContactRepository repository, UsuarioLookup users, Autorizacao autorizacao,
                           EntityManager entityManager) {
        this.repository = repository;
        this.users = users;
        this.autorizacao = autorizacao;
        this.entityManager = entityManager;
    }

    @Transactional
    ContactEntity criar(ContactDtos.ContatoRequest requisicao, UUID autorId, String idempotencyKey) {
        autorizacao.exigir(Permissao.CONTACTS_WRITE);
        UUID tenantId = TenantContext.obrigatorio();
        UUID responsavelId = autorizacao.responsavelPadrao(
                Permissao.CONTACTS_WRITE, requisicao.responsavelId());

        // O alcance é decidido antes da consulta de existência para não usar
        // 403/404 como oráculo de usuários ativos de uma empresa.
        autorizacao.exigirSobreRegistro(Permissao.CONTACTS_WRITE, responsavelId);
        if (responsavelId != null && !users.existsActive(tenantId, responsavelId)) {
            throw new RecursoNaoEncontradoException("Responsável");
        }

        String chave = ContactCreationPolicy.validarChave(idempotencyKey);
        if (chave != null) {
            repository.bloquearCriacaoIdempotente(
                    ContactCreationPolicy.identificadorDoBloqueio(tenantId, chave));
            var existente = repository.findByTenantIdAndIdempotencyKey(tenantId, chave);
            if (existente.isPresent()) {
                ContactEntity contato = existente.orElseThrow();
                if (!contato.correspondeA(requisicao, responsavelId)) {
                    throw new ChaveIdempotenciaContatoEmConflitoException();
                }
                return contato;
            }
        }

        ContactEntity contato = ContactEntity.novo(tenantId, autorId);
        contato.aplicar(requisicao.tipo(), requisicao.nome(), requisicao.email(),
                requisicao.telefone(), requisicao.empresa(), requisicao.observacoes(),
                responsavelId, autorId);
        contato.definirChaveDeIdempotencia(chave);
        ContactEntity persistido = repository.saveAndFlush(contato);
        // created_at é autoridade do banco; o refresh garante que a resposta
        // da mesma requisição já cumpra o contrato, sem esperar nova consulta.
        entityManager.refresh(persistido);
        return persistido;
    }
}
