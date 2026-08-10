package br.com.pnp.crm.integration.internal;

import org.apache.hc.client5.http.DnsResolver;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Resolve uma vez, rejeita qualquer endereço não público e devolve o snapshot fixo. */
@Component
class PoliticaAntiSsrf {

    private static final Set<String> HOSTS_METADATA = Set.of(
            "metadata", "metadata.google.internal", "instance-data",
            "metadata.azure.internal");

    private final ResolucaoDns dns;
    private final boolean permitirHttp;
    private final boolean permitirPrivadoParaTeste;

    PoliticaAntiSsrf() {
        this(InetAddress::getAllByName, false, false);
    }

    PoliticaAntiSsrf(ResolucaoDns dns, boolean permitirHttp,
                     boolean permitirPrivadoParaTeste) {
        this.dns = dns;
        this.permitirHttp = permitirHttp;
        this.permitirPrivadoParaTeste = permitirPrivadoParaTeste;
    }

    DestinoAprovado aprovar(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw bloqueado();
        }
        String esquema = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("https") && !(permitirHttp && esquema.equals("http"))) {
            throw bloqueado();
        }
        String host;
        try {
            host = IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw bloqueado();
        }
        if (host.endsWith(".") || HOSTS_METADATA.contains(host)
                || host.endsWith(".metadata.google.internal")) {
            throw bloqueado();
        }
        int porta = uri.getPort();
        if (porta == 0 || porta > 65535) throw bloqueado();

        InetAddress[] resolvidos;
        try {
            resolvidos = dns.resolver(host);
        } catch (UnknownHostException e) {
            throw new DestinoIndisponivelException();
        }
        if (resolvidos == null || resolvidos.length == 0 || resolvidos.length > 16) {
            throw bloqueado();
        }
        InetAddress[] snapshot = resolvidos.clone();
        if (!permitirPrivadoParaTeste) {
            for (InetAddress endereco : snapshot) {
                if (!publico(endereco)) throw bloqueado();
            }
        }
        return new DestinoAprovado(uri, host, snapshot);
    }

    DnsResolver fixar(DestinoAprovado destino) {
        InetAddress[] snapshot = destino.enderecos().clone();
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                String normalizado = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
                        .toLowerCase(Locale.ROOT);
                if (!normalizado.equals(destino.host())) throw new UnknownHostException();
                return snapshot.clone();
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                resolve(host);
                return destino.host();
            }
        };
    }

    private static boolean publico(InetAddress endereco) {
        if (endereco == null || endereco.isAnyLocalAddress()
                || endereco.isLoopbackAddress() || endereco.isLinkLocalAddress()
                || endereco.isSiteLocalAddress() || endereco.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = endereco.getAddress();
        if (endereco instanceof Inet4Address) return ipv4Publico(bytes);
        if (!(endereco instanceof Inet6Address) || bytes.length != 16) return false;

        byte[] mapeado = ipv4Mapeado(bytes);
        if (mapeado != null) return ipv4Publico(mapeado);

        // Somente Global Unicast 2000::/3, removendo blocos especiais capazes
        // de encapsular IPv4 ou reservados para documentação/benchmark.
        if ((bytes[0] & 0xe0) != 0x20) return false;
        if (prefixo(bytes, new int[]{0x20, 0x01, 0x0d, 0xb8}, 32)      // documentação
                || prefixo(bytes, new int[]{0x20, 0x01, 0x00, 0x00}, 32) // Teredo/especial
                || prefixo(bytes, new int[]{0x20, 0x01, 0x00, 0x02, 0x00, 0x00}, 48) // benchmark
                || prefixo(bytes, new int[]{0x20, 0x02}, 16)) {           // 6to4
            return false;
        }
        return true;
    }

    private static boolean ipv4Publico(byte[] b) {
        int a = b[0] & 0xff;
        int c = b[1] & 0xff;
        int d = b[2] & 0xff;
        return !(a == 0 || a == 10 || a == 127 || a >= 224
                || (a == 100 && c >= 64 && c <= 127)
                || (a == 169 && c == 254)
                || (a == 172 && c >= 16 && c <= 31)
                || (a == 192 && c == 168)
                || (a == 192 && c == 0 && d == 0)
                || (a == 192 && c == 0 && d == 2)
                || (a == 192 && c == 88 && d == 99)
                || (a == 198 && (c == 18 || c == 19))
                || (a == 198 && c == 51 && d == 100)
                || (a == 203 && c == 0 && d == 113));
    }

    private static byte[] ipv4Mapeado(byte[] b) {
        boolean zeros = true;
        for (int i = 0; i < 10; i++) zeros &= b[i] == 0;
        if (zeros && b[10] == (byte) 0xff && b[11] == (byte) 0xff) {
            return Arrays.copyOfRange(b, 12, 16);
        }
        return null;
    }

    private static boolean prefixo(byte[] valor, int[] prefixo, int bits) {
        int bytesInteiros = bits / 8;
        for (int i = 0; i < bytesInteiros; i++) {
            if ((valor[i] & 0xff) != prefixo[i]) return false;
        }
        return true;
    }

    private static DestinoBloqueadoException bloqueado() {
        return new DestinoBloqueadoException();
    }

    @FunctionalInterface
    interface ResolucaoDns {
        InetAddress[] resolver(String host) throws UnknownHostException;
    }

    record DestinoAprovado(URI uri, String host, InetAddress[] enderecos) {
        DestinoAprovado {
            enderecos = enderecos.clone();
        }

        @Override
        public InetAddress[] enderecos() {
            return enderecos.clone();
        }
    }

    static final class DestinoBloqueadoException extends RuntimeException {
        DestinoBloqueadoException() {
            super("Destino HTTP bloqueado pela politica de rede.");
        }
    }

    static final class DestinoIndisponivelException extends RuntimeException {
        DestinoIndisponivelException() {
            super("Destino HTTP temporariamente indisponivel.");
        }
    }
}
