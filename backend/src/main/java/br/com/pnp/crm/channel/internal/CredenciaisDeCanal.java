package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Guarda e recupera credenciais de canal, sempre cifradas em repouso.
 *
 * <p>É a única porta para o material sensível, e fica dentro de
 * {@code channel.internal} — nenhum outro módulo alcança daqui, nem deveria
 * querer. Um segredo que atravessa fronteira de módulo é um segredo com mais
 * lugares de onde vazar.
 */
@Service
class CredenciaisDeCanal {

    private final ChannelCredentialRepository repository;
    private final CofreDeCredenciais cofre;

    CredenciaisDeCanal(ChannelCredentialRepository repository, CofreDeCredenciais cofre) {
        this.repository = repository;
        this.cofre = cofre;
    }

    @Transactional
    void guardar(UUID tenantId, UUID channelConnectionId, TipoCredencial tipo, String valorEmClaro) {
        // Substitui a anterior em vez de acumular: credencial antiga guardada
        // "por precaução" é credencial válida esquecida em produção.
        repository.findByTenantIdAndChannelConnectionIdAndKindAndDeletedAtIsNull(
                        tenantId, channelConnectionId, tipo)
                .ifPresent(repository::delete);

        repository.save(ChannelCredentialEntity.nova(
                tenantId, channelConnectionId, tipo, cofre.cifrar(valorEmClaro)));
    }

    /**
     * @return o valor em claro, ou vazio quando não há credencial cadastrada.
     * Nunca lance com o valor na mensagem; nunca registre o retorno em log.
     */
    @Transactional(readOnly = true)
    Optional<String> recuperar(UUID channelConnectionId, TipoCredencial tipo) {
        return repository
                .findByTenantIdAndChannelConnectionIdAndKindAndDeletedAtIsNull(
                        TenantContext.obrigatorio(), channelConnectionId, tipo)
                .map(ChannelCredentialEntity::getCiphertext)
                .map(cofre::decifrar);
    }
}
