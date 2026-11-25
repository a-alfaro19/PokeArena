package cr.ac.tec.pokearena.domain.rule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.MatchSearch;

/** Amplía la diferencia de Elo admitida con el tiempo de espera de cada jugador (A.10). */
public record EloWindowRules(int initialRange, int step, Duration stepInterval, int maxRange) {

    public Optional<MatchSearch> pickCompatible(MatchSearch anchor, List<MatchSearch> candidates, Instant now) {
        // El repositorio ya ordenó las candidatas. La espera más larga determina el rango de la pareja.
        int anchorRange = rangeAt(anchor.createdAt(), now);
        return candidates.stream().filter(candidate ->
                Math.abs((long) anchor.elo() - candidate.elo()) <=
                        Math.max(anchorRange, rangeAt(candidate.createdAt(), now)))
                .findFirst();
    }

    public int rangeAt(Instant createdAt, Instant now) {
        // El reloj puede retroceder. En ese caso todavía se admite el rango inicial.
        if (step == 0 || !now.isAfter(createdAt)) {
            return initialRange;
        }
        long steps = Duration.between(createdAt, now).dividedBy(stepInterval);
        // Acotar antes de multiplicar evita que las esperas largas desborden el cálculo del rango.
        long stepsToMax = (maxRange - initialRange + (long) step - 1) / step;
        return (int) Math.min(maxRange, initialRange + Math.min(steps, stepsToMax) * step);
    }
}
