package br.com.pnp.crm.identity.internal;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Emissão do access token.
 *
 * <p><b>Validade de 15 minutos e nenhuma revogação:</b> o token não é
 * consultado no banco a cada requisição, e é por isso que ele é curto. Uma
 * lista de revogação consultada a cada chamada devolveria o custo que o JWT
 * existe para evitar, e ainda assim só reduziria a janela de abuso de 15
 * minutos para alguns segundos. Quem precisa de corte imediato usa o refresh:
 * revogar a família mata a sessão na próxima renovação.
 *
 * <p><b>HMAC e não RSA:</b> emissor e verificador são o mesmo serviço. Chave
 * assimétrica só paga a pena quando um terceiro precisa verificar sem poder
 * emitir, e não é o caso hoje. Se um dia o agente privado precisar validar
 * tokens, isso vira RS256 — e é uma troca de bean, não de arquitetura.
 */
@Service
class AccessTokenService {

    static final Duration VALIDADE = Duration.ofMinutes(15);

    private static final String EMISSOR = "crm-pnp";
    static final String CLAIM_TENANT = "tid";
    static final String CLAIM_LOGIN = "login";

    // HS256 exige chave de no mínimo 256 bits. Abaixo disso o Nimbus recusa a
    // emissão, o que é o comportamento certo: chave curta de HMAC é quebrável.
    private static final int TAMANHO_MINIMO_CHAVE_BYTES = 32;

    private final JwtEncoder encoder;

    AccessTokenService(@Value("${app.security.jwt-signing-key}") String chaveAssinatura) {
        byte[] bytes = chaveAssinatura == null
                ? new byte[0]
                : chaveAssinatura.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_CHAVE_BYTES) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY precisa de pelo menos " + TAMANHO_MINIMO_CHAVE_BYTES
                            + " bytes. A aplicação não sobe com chave fraca.");
        }
        this.encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(bytes, "HmacSHA256")));
    }

    String emitir(UUID usuarioId, UUID tenantId, String login) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .issuedAt(agora)
                .expiresAt(agora.plus(VALIDADE))
                // subject é o id, nunca o login: o login pode ser alterado e o
                // token continuaria apontando para um usuário que não existe
                // mais com aquele nome.
                .subject(usuarioId.toString())
                .claim(CLAIM_TENANT, tenantId.toString())
                .claim(CLAIM_LOGIN, login)
                .build();

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
