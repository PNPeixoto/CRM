package br.com.pnp.crm.identity.internal;

interface PasswordResetDelivery {
    void deliver(String email, String clearToken);
}
