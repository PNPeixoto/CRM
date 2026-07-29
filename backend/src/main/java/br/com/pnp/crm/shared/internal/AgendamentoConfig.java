package br.com.pnp.crm.shared.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendamento de tarefas.
 *
 * <p>Separado da classe da aplicação de propósito: um teste de integração que
 * suba o contexto não deveria disparar o worker de envio junto e começar a
 * consumir a fila enquanto o teste monta o cenário. Com a anotação isolada
 * aqui, excluir esta configuração no teste é uma linha.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AgendamentoConfig {
}
