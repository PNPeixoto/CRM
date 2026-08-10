package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuarentenaDeMidiaTest {

    @TempDir Path temporario;

    @Test
    void jpegRecebeNomeGeradoEFicaSegregadoPorTenant() throws Exception {
        QuarentenaDeMidia storage = new QuarentenaDeMidia(temporario.toString());
        UUID tenant = UUID.randomUUID();
        UUID media = UUID.randomUUID();
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};

        var salva = storage.armazenar(tenant, media, new ByteArrayInputStream(jpeg));

        assertThat(salva.tipoDetectado()).isEqualTo("image/jpeg");
        assertThat(salva.storageKey()).startsWith(tenant.toString() + "/");
        assertThat(salva.storageKey()).doesNotContain("..", "jpeg", "arquivo-original");
        assertThat(Files.readAllBytes(temporario.resolve(salva.storageKey())))
                .containsExactly(jpeg);
    }

    @Test
    void htmlESvgExecutavelSaoRecusadosERemovidos() throws Exception {
        QuarentenaDeMidia storage = new QuarentenaDeMidia(temporario.toString());
        UUID tenant = UUID.randomUUID();

        assertThatThrownBy(() -> storage.armazenar(
                tenant, UUID.randomUUID(),
                new ByteArrayInputStream("<svg><script/></svg>".getBytes())))
                .isInstanceOf(EnvioDeMensagemException.class)
                .hasMessageContaining("nao permitido");

        try (var arquivos = Files.walk(temporario)) {
            assertThat(arquivos.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void chaveDeExpurgoNaoEscapaDaRaiz() {
        QuarentenaDeMidia storage = new QuarentenaDeMidia(temporario.toString());
        assertThatThrownBy(() -> storage.remover("../../fora.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
