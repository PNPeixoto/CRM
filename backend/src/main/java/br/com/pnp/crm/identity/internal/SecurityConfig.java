package br.com.pnp.crm.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    /** Únicas portas sem token de acesso. Tudo o mais exige autenticação. */
    private static final String[] ENDPOINTS_PUBLICOS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health/**",
            "/error"
    };

    /**
     * O handshake do WebSocket precisa passar pelo filtro HTTP sem token, e
     * isso <b>não</b> o torna público: a API {@code WebSocket} do navegador não
     * permite definir cabeçalhos no handshake, então a autenticação acontece
     * uma camada adentro, no frame STOMP CONNECT, pelo
     * {@code StompAuthChannelInterceptor}. Uma conexão que chegue até aqui e
     * não apresente credencial no CONNECT é derrubada antes de receber
     * qualquer coisa.
     *
     * <p>A origem do handshake é conferida contra a mesma lista branca do CORS
     * na configuração do endpoint STOMP — necessário porque a política de mesma
     * origem do navegador não se aplica a WebSocket.
     */
    private static final String ENDPOINT_WEBSOCKET = "/ws/**";

    /**
     * Webhooks de provedor não têm sessão e não podem ter.
     *
     * <p>Quem chama é o Telegram, não um usuário: exigir token de acesso aqui
     * simplesmente impediria o canal de funcionar. A autenticidade vem da
     * assinatura — no Telegram, o {@code secret_token} conferido em tempo
     * constante dentro do controller; na Meta, o HMAC sobre o corpo cru.
     *
     * <p>CSRF também sai, e não é descuido: proteção contra CSRF existe para
     * requisição que o navegador faz carregando credencial de sessão
     * automaticamente. Não há navegador, não há sessão e não há cookie neste
     * caminho.
     */
    private static final String ENDPOINT_WEBHOOKS = "/api/webhooks/**";

    private final String origensPermitidas;
    private final boolean cspExigeTlsNoWebsocket;

    SecurityConfig(@Value("${app.cors.allowed-origins:}") String origensPermitidas,
                   @Value("${app.security.websocket-exige-tls:true}") boolean cspExigeTlsNoWebsocket) {
        this.origensPermitidas = origensPermitidas;
        this.cspExigeTlsNoWebsocket = cspExigeTlsNoWebsocket;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        CookieCsrfTokenRepository csrfRepository = csrfTokenRepository();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Login e refresh não têm sessão a proteger no momento
                        // em que são chamados. Mantê-los sob CSRF impediria o
                        // primeiro login de quem chega sem cookie algum.
                        // O handshake do WebSocket não é uma mutação de estado
                        // e não carrega cookie de sessão como credencial — a
                        // credencial dele é o token no frame CONNECT.
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh",
                                ENDPOINT_WEBSOCKET, ENDPOINT_WEBHOOKS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(politicaCsp()))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(cto -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // X-XSS-Protection sai. O cabeçalho está depreciado, os
                        // navegadores modernos o ignoram, e nos antigos o filtro
                        // chegou a criar vulnerabilidade própria. O valor
                        // recomendado hoje é 0, que é o que o Spring escreve
                        // quando a proteção é desabilitada.
                        .xssProtection(xss -> xss.disable()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ENDPOINTS_PUBLICOS).permitAll()
                        .requestMatchers(ENDPOINT_WEBSOCKET).permitAll()
                        .requestMatchers(ENDPOINT_WEBHOOKS).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .addFilterAfter(new CsrfCookieFilter(csrfRepository), CsrfFilter.class)
                // Depois do filtro anônimo, que é o último da cadeia de
                // autenticação: neste ponto o JWT já foi verificado e o
                // SecurityContext está preenchido.
                .addFilterAfter(new TenantContextFilter(), AnonymousAuthenticationFilter.class);

        return http.build();
    }

    /**
     * <b>connect-src precisa listar WebSocket explicitamente.</b> O
     * {@code 'self'} não cobre os esquemas {@code ws:}/{@code wss:}, e sem
     * eles o navegador bloqueia o handshake do STOMP — a conexão de tempo real
     * simplesmente não abre, com erro de CSP no console e nada no servidor.
     *
     * <p>Em produção só {@code wss:} é aceito: {@code ws:} é texto puro, e
     * permitir os dois deixa a porta aberta para rebaixamento.
     */
    private String politicaCsp() {
        String websocket = cspExigeTlsNoWebsocket ? "wss:" : "ws: wss:";
        return "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data: blob:; "
                + "connect-src 'self' " + websocket + "; "
                + "font-src 'self' data:; "
                + "base-uri 'self'; "
                + "form-action 'self'; "
                + "object-src 'none'; "
                + "frame-ancestors 'none'";
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-signing-key}") String chave) {
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(chave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(listaBrancaDeOrigens());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Requested-With", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Lista vazia quando a variável não está definida — ou seja, na ausência
     * de configuração <b>nenhuma</b> origem é aceita. É o oposto do
     * {@code *} que costuma virar padrão, e é o comportamento certo: uma
     * variável esquecida quebra o frontend de forma visível, em vez de abrir
     * a API de forma invisível.
     */
    private List<String> listaBrancaDeOrigens() {
        if (origensPermitidas == null || origensPermitidas.isBlank()) {
            return List.of();
        }
        return Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isBlank())
                .toList();
    }

    /**
     * {@code withHttpOnlyFalse} é intencional e não é uma falha: o padrão
     * double-submit exige que o JavaScript leia este cookie para reenviá-lo
     * como cabeçalho. O token de CSRF não é credencial — ele só prova que a
     * requisição foi montada por código da própria origem.
     */
    private CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }
}
