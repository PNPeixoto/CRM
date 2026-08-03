package br.com.pnp.crm.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Component
@ConditionalOnProperty(name = "app.security.password-reset-delivery-enabled", havingValue = "true")
final class HttpPasswordResetDelivery implements PasswordResetDelivery {

    private final RestClient restClient;
    private final URI endpoint;

    HttpPasswordResetDelivery(
            @Value("${app.security.password-reset-delivery-url:}") String endpoint) {
        this.endpoint = URI.create(endpoint == null ? "" : endpoint.trim());
        if (!"https".equalsIgnoreCase(this.endpoint.getScheme())) {
            throw new IllegalStateException(
                    "PASSWORD_RESET_DELIVERY_URL precisa usar HTTPS quando a entrega está ativa.");
        }
        this.restClient = RestClient.create();
    }

    @Override
    public void deliver(String email, String clearToken) {
        restClient.post().uri(endpoint)
                .body(new DeliveryRequest(email, clearToken))
                .retrieve().toBodilessEntity();
    }

    private record DeliveryRequest(String destinatario, String codigo) {
    }
}

@Component
@ConditionalOnProperty(name = "app.security.password-reset-delivery-enabled",
        havingValue = "false", matchIfMissing = true)
final class DisabledPasswordResetDelivery implements PasswordResetDelivery {

    private static final Logger log = LoggerFactory.getLogger(DisabledPasswordResetDelivery.class);

    @Override
    public void deliver(String email, String clearToken) {
        // Nem destinatário nem token entram no log. Em produção esta adaptação
        // deve estar desativada e a URL HTTPS de entrega deve ser obrigatória.
        log.info("Entrega de recuperação de senha desabilitada neste ambiente.");
    }
}
