package cr.ac.tec.pokearena.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.application.usecase.RunMatchmakingUseCase;

/** Invoca el emparejador en cada instancia habilitada y registra fallos del ciclo (A.10). */
@Component
@ConditionalOnProperty(name = "app.matchmaking.enabled", havingValue = "true")
public class MatchmakingJob {
    private static final Logger LOG = LoggerFactory.getLogger(MatchmakingJob.class);

    private final RunMatchmakingUseCase matchmaking;
    private final ObjectProvider<StartMatch> start;

    public MatchmakingJob(RunMatchmakingUseCase matchmaking, ObjectProvider<StartMatch> start) {
        this.matchmaking = matchmaking;
        this.start = start;
    }

    @Scheduled(fixedDelayString = "${app.matchmaking.tick-interval}",
            initialDelayString = "${app.matchmaking.tick-interval}")
    public void run() {
        try {
            // La US 48 instala el preparador junto con V4 y V5. Hasta entonces el ciclo no accede a sus tablas.
            if (start.getIfAvailable() != null) {
                matchmaking.execute();
            }
        } catch (RuntimeException failure) {
            LOG.error("Falló el ciclo de emparejamiento. Se volverá a ejecutar en el siguiente intervalo.", failure);
        }
    }
}
