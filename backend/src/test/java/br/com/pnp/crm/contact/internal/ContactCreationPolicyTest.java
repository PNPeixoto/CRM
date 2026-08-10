package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactCreationPolicyTest {

    private static final UUID TENANT = UUID.fromString("019fa91c-0f63-75f7-b4a0-1494c1304c42");

    @Test
    void aceitaChaveSeguraEOpcional() {
        assertThat(ContactCreationPolicy.validarChave(null)).isNull();
        assertThat(ContactCreationPolicy.validarChave("contato:2026-0001"))
                .isEqualTo("contato:2026-0001");
    }

    @Test
    void rejeitaChaveCurtaOuComCaracteresDeCabecalho() {
        assertThatThrownBy(() -> ContactCreationPolicy.validarChave("curta"))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> ContactCreationPolicy.validarChave("contato\r\ninjetado"))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void bloqueioEhEstavelPorTenantEChave() {
        long primeiro = ContactCreationPolicy.identificadorDoBloqueio(TENANT, "contato:2026-0001");
        assertThat(ContactCreationPolicy.identificadorDoBloqueio(TENANT, "contato:2026-0001"))
                .isEqualTo(primeiro);
        assertThat(ContactCreationPolicy.identificadorDoBloqueio(TENANT, "contato:2026-0002"))
                .isNotEqualTo(primeiro);
    }
}
