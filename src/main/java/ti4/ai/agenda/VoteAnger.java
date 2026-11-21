package ti4.ai.agenda;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.Hostility;
import ti4.ai.tactical.TacticalStrength;
import ti4.ai.trade.Relations;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class VoteAnger {

    static final double ANGER_SHARE = 0.15;
    static final double MIN_HARM = 1.0;
    static final double MIN_ANGER = 0.05;
    static final int RUNAWAY_LEAD = 2;
    static final int NEAR_GOAL = 2;

    static double ofBallot(Game game, Player voter, AgendaForecast forecast, String outcome, int votes) {
        if (votes <= 0 || outcome.isEmpty() || !AiExperiments.enabled(voter, Experiment.VOTE_ANGER)) return 0;
        Map<String, Double> weights = new HashMap<>();
        for (Player other : game.getRealPlayers()) {
            if (other != voter) {
                weights.put(
                        other.getFaction(),
                        TacticalStrength.angerWeight(game, voter, other) * Hostility.spreadFactor(game, voter, other));
            }
        }
        Map<String, Double> angers = new HashMap<>();
        for (String alternative : forecast.outcomes())
            angers.put(alternative, anger(game, voter, forecast, alternative, weights));
        double without = forecast.expected(alternative -> angers.getOrDefault(alternative, 0.0));
        double with = forecast.withVotes(voter, outcome, votes)
                .expected(alternative -> angers.getOrDefault(alternative, 0.0));
        double added = with - without;
        return added < MIN_ANGER ? 0 : added;
    }

    static double anger(Game game, Player voter, AgendaForecast forecast, String outcome, Map<String, Double> weights) {
        List<Player> others = game.getRealPlayers().stream()
                .filter(other -> !other.getFaction().equals(voter.getFaction()))
                .toList();
        double tableHarm = others.stream()
                .mapToDouble(other -> harm(forecast, other, outcome))
                .average()
                .orElse(0);
        return others.stream()
                .filter(other -> !Relations.bad(game, voter, other))
                .filter(other -> !mustBeStopped(game, other))
                .filter(other -> harm(forecast, other, outcome) - tableHarm >= MIN_HARM)
                .mapToDouble(other -> ANGER_SHARE
                        * (harm(forecast, other, outcome) - tableHarm)
                        * weights.getOrDefault(other.getFaction(), 1.0))
                .sum();
    }

    static double harm(AgendaForecast forecast, Player victim, String outcome) {
        double others = forecast.outcomes().stream()
                .filter(alternative -> !alternative.equals(outcome))
                .mapToDouble(alternative -> forecast.utility(victim, alternative))
                .average()
                .orElse(forecast.utility(victim, outcome));
        return Math.max(0, others - forecast.utility(victim, outcome));
    }

    private static boolean mustBeStopped(Game game, Player other) {
        int points = other.getTotalVictoryPoints();
        if (points >= game.getVp() - NEAR_GOAL) return true;
        int nextBest = game.getRealPlayers().stream()
                .filter(player -> player != other)
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
        return points - nextBest >= RUNAWAY_LEAD;
    }
}
