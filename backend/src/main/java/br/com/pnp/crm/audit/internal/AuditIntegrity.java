package br.com.pnp.crm.audit.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class AuditIntegrity {

    private AuditIntegrity() {
    }

    static String calcular(AuditRecord registro) {
        String canonico = String.join("|",
                campo(registro.id()),
                campo(registro.tenantId()),
                campo(registro.occurredAt()),
                campo(registro.actorType()),
                campo(registro.actorId()),
                campo(registro.scopeType()),
                campo(registro.scopeId()),
                campo(registro.action().codigo()),
                campo(registro.targetType()),
                campo(registro.targetId()),
                campo(registro.outcome()),
                campo(registro.reason()),
                campo(registro.correlationId()),
                campo(registro.action().categoriaRetencao()),
                campo(registro.integrityVersion()));
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel.", e);
        }
    }

    private static String campo(Object valor) {
        String texto = valor == null ? "" : valor.toString();
        return texto.length() + ":" + texto;
    }
}

