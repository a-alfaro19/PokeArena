package cr.ac.tec.pokearena.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.usecase.RunMatchmakingUseCase;

/** Invoca el emparejador en cada instancia habilitada y registra fallos del ciclo (A.10). */
@Component
@ConditionalOnProperty(name = "app.matchmaking.enabled", havingValue = "true")
public class MatchmakingJob {
    private static final Logger LOG = LoggerFactory.getLogger(MatchmakingJob.class);

    private final RunMatchmakingUseCase matchmaking;

    public MatchmakingJob(RunMatchmakingUseCase matchmaking) {
        this.matchmaking = matchmaking;
    }

    @Scheduled(fixedDelayString = "${app.matchmaking.tick-interval}",
            initialDelayString = "${app.matchmaking.tick-interval}")
    public void run() {
        // Registrar un fallo del ciclo permite que el siguiente vuelva a ejecutarse.
        try {
            matchmaking.execute();
        } catch (RuntimeException failure) {
            LOG.error("Falló el ciclo de emparejamiento. Se volverá a ejecutar en el siguiente intervalo.", failure);
        }
    }
}
