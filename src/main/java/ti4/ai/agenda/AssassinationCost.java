package ti4.ai.agenda;

import java.util.OptionalDouble;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class AssassinationCost {

    private static final String END_PHASE = "agendaEnd";

    public static OptionalDouble whenVotesAreIn(Game game, Player victim) {
        if (!END_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) return OptionalDouble.empty();
        AgendaForecast forecast = AgendaForecast.of(game, victim);
        if (forecast.isEmpty()) return OptionalDouble.empty();
        int votes = VoteCapacity.of(game, victim);
        double actual = forecast.expectedUtility(victim);
        double best = forecast.outcomes().stream()
                .mapToDouble(
                        outcome -> forecast.withVotes(victim, outcome, votes).expectedUtility(victim))
                .max()
                .orElse(actual);
        return OptionalDouble.of(Math.max(0, best - actual));
    }
}
