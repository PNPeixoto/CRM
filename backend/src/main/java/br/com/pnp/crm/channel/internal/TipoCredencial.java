package br.com.pnp.crm.channel.internal;

/**
 * Espelha o CHECK de {@code channel_credential.kind}.
 *
 * <p>Interno ao módulo {@code channel} de propósito: nenhum outro módulo tem
 * motivo para saber que tipos de segredo existem, quanto mais para pedir um.
 */
enum TipoCredencial {

    /** Token do bot, obtido no @BotFather. Autentica o CRM perante o Telegram. */
    TELEGRAM_BOT_TOKEN,

    /**
     * Valor definido por nós no {@code setWebhook} e devolvido pelo Telegram no
     * header {@code X-Telegram-Bot-Api-Secret-Token}. Autentica o Telegram
     * perante o CRM — é o sentido inverso do anterior, e por isso são dois
     * segredos distintos.
     */
    TELEGRAM_WEBHOOK_SECRET,

    META_ACCESS_TOKEN,

    META_APP_SECRET,

    /** Segredo enviado pela Evolution no header do webhook desta conexao. */
    EVOLUTION_WEBHOOK_SECRET
}
