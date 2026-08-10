package br.com.pnp.crm.integration.internal;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaAntiSsrfTest {

    @Test
    void bloqueiaIpv4PrivadoReservadoMetadataEMisturaComPublico() throws Exception {
        String[] bloqueados = {
                "0.0.0.1", "10.0.0.1", "100.64.0.1", "127.0.0.1",
                "169.254.169.254", "172.16.0.1", "192.168.0.1",
                "192.0.0.1", "192.0.2.1", "192.88.99.1", "198.18.0.1",
                "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1"
        };
        for (String endereco : bloqueados) {
            var politica = politica(endereco);
            assertThatThrownBy(() -> politica.aprovar(URI.create("https://destino.test")))
                    .as(endereco)
                    .isInstanceOf(PoliticaAntiSsrf.DestinoBloqueadoException.class);
        }

        var misto = new PoliticaAntiSsrf(host -> new InetAddress[]{
                InetAddress.getByName("1.1.1.1"), InetAddress.getByName("10.0.0.1")
        }, false, false);
        assertThatThrownBy(() -> misto.aprovar(URI.create("https://destino.test")))
                .isInstanceOf(PoliticaAntiSsrf.DestinoBloqueadoException.class);
        assertThatThrownBy(() -> politica("1.1.1.1")
                .aprovar(URI.create("https://metadata.google.internal/latest")))
                .isInstanceOf(PoliticaAntiSsrf.DestinoBloqueadoException.class);
    }

    @Test
    void bloqueiaIpv6LocalReservadoMapeadoETuneis() throws Exception {
        String[] bloqueados = {
                "::", "::1", "fe80::1", "fc00::1", "ff02::1",
                "2001:db8::1", "2001::1", "2001:2::1", "2002::1",
                "::ffff:10.0.0.1"
        };
        for (String endereco : bloqueados) {
            assertThatThrownBy(() -> politica(endereco)
                    .aprovar(URI.create("https://destino.test")))
                    .as(endereco)
                    .isInstanceOf(PoliticaAntiSsrf.DestinoBloqueadoException.class);
        }
    }

    @Test
    void aceitaSomenteEnderecosPublicosEExigeHttps() throws Exception {
        assertThat(politica("1.1.1.1").aprovar(URI.create("https://destino.test"))
                .enderecos()).extracting(InetAddress::getHostAddress).containsExactly("1.1.1.1");
        assertThat(politica("2606:4700:4700::1111")
                .aprovar(URI.create("https://destino.test")).enderecos()).hasSize(1);
        assertThatThrownBy(() -> politica("1.1.1.1")
                .aprovar(URI.create("http://destino.test")))
                .isInstanceOf(PoliticaAntiSsrf.DestinoBloqueadoException.class);
    }

    @Test
    void fixaPrimeiraResolucaoEImpedeDnsRebinding() throws Exception {
        AtomicInteger consultas = new AtomicInteger();
        var politica = new PoliticaAntiSsrf(host -> consultas.incrementAndGet() == 1
                ? new InetAddress[]{InetAddress.getByName("1.1.1.1")}
                : new InetAddress[]{InetAddress.getByName("127.0.0.1")}, false, false);

        var destino = politica.aprovar(URI.create("https://destino.test/recurso"));
        InetAddress[] usadoNaConexao = politica.fixar(destino).resolve("destino.test");

        assertThat(consultas).hasValue(1);
        assertThat(usadoNaConexao).extracting(InetAddress::getHostAddress)
                .containsExactly("1.1.1.1");
    }

    private static PoliticaAntiSsrf politica(String endereco) throws Exception {
        InetAddress resolvido = InetAddress.getByName(endereco);
        return new PoliticaAntiSsrf(host -> new InetAddress[]{resolvido}, false, false);
    }
}
