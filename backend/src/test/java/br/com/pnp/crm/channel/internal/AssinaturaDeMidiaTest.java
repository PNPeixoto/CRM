package br.com.pnp.crm.channel.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaDeMidiaTest {

    private final AssinaturaDeMidia assinatura = new AssinaturaDeMidia(
            Base64.getEncoder().encodeToString("chave-de-teste-media-32-bytes!!!".getBytes()));

    @Test
    void assinaturaVinculaTenantMidiaEExpiracao() {
        UUID tenant = UUID.randomUUID();
        UUID media = UUID.randomUUID();
        long expira = Instant.now().plusSeconds(60).getEpochSecond();
        String valor = assinatura.assinar(tenant, media, expira);

        assertThat(assinatura.conferir(tenant, media, expira, valor)).isTrue();
        assertThat(assinatura.conferir(UUID.randomUUID(), media, expira, valor)).isFalse();
        assertThat(assinatura.conferir(tenant, UUID.randomUUID(), expira, valor)).isFalse();
        assertThat(assinatura.conferir(tenant, media, expira - 120, valor)).isFalse();
        assertThat(assinatura.conferir(tenant, media, expira, valor + "00")).isFalse();
    }
}
