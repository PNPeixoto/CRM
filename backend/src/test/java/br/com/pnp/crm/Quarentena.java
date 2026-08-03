package br.com.pnp.crm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exceção temporária para um teste instável que continua sendo executado.
 *
 * <p>Não substitui falha por sucesso: a extensão valida os metadados e reprova
 * a suíte quando a data expira. Segurança, tenant, migration e cobrança nunca
 * podem receber esta anotação.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("quarentena")
@ExtendWith(ValidaQuarentenaExtension.class)
public @interface Quarentena {

    String issue();

    String responsavel();

    String justificativa();

    /** Data ISO-8601, por exemplo 2026-08-15. */
    String expiraEm();

    Categoria categoria();

    enum Categoria {
        FUNCIONAL(false),
        PROVEDOR(false),
        INTERFACE(false),
        SEGURANCA(true),
        TENANT(true),
        MIGRATION(true),
        COBRANCA(true);

        private final boolean proibida;

        Categoria(boolean proibida) {
            this.proibida = proibida;
        }

        boolean proibida() {
            return proibida;
        }
    }
}
