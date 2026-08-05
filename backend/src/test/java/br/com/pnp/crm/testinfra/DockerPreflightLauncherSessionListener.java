package br.com.pnp.crm.testinfra;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.testcontainers.DockerClientFactory;

/** Interrompe a suíte uma única vez quando a infraestrutura está ausente. */
public final class DockerPreflightLauncherSessionListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (!Boolean.parseBoolean(System.getProperty("crm.test.docker.preflight", "true"))) {
            return;
        }
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException(
                    "INFRAESTRUTURA INDISPONÍVEL: inicie o Docker antes de executar a suíte completa. "
                            + "Para testes sem containers, use -Pgate-rapido.");
        }
    }
}
