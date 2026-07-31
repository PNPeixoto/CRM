package br.com.pnp.crm.contact.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

final class ContactDtos {

    private ContactDtos() {
    }

    /**
     * Note o que não está aqui: {@code id}, {@code tenantId} e as colunas de
     * auditoria. Aceitar qualquer um deles no corpo permitiria ao cliente
     * escolher em nome de quem, e em qual empresa, o registro é criado.
     */
    record ContatoRequest(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 200)
            String nome,

            @Email(message = "E-mail inválido.")
            @Size(max = 200)
            String email,

            @Size(max = 40)
            String telefone,

            @Size(max = 200)
            String empresa,

            @Size(max = 4000)
            String observacoes,

            UUID responsavelId) {
    }

    record ContatoResponse(
            UUID id,
            String nome,
            String email,
            String telefone,
            String empresa,
            String observacoes,
            UUID responsavelId,
            Instant criadoEm) {
    }
}
