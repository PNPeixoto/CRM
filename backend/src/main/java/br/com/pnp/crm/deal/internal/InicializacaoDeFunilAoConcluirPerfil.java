package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.tenant.api.PerfilInicialConcluido;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Materializa o funil no mesmo commit que conclui o perfil inicial. */
@Component
class InicializacaoDeFunilAoConcluirPerfil {

    private final FunilPadraoService funis;

    InicializacaoDeFunilAoConcluirPerfil(FunilPadraoService funis) {
        this.funis = funis;
    }

    @EventListener
    void aoConcluir(PerfilInicialConcluido evento) {
        funis.criarDoPerfilInicial(
                evento.tenantId(), evento.autorId(), evento.funilPadrao());
    }
}
