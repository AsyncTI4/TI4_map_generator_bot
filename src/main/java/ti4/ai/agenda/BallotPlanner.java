package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class BallotPlanner {

    private static final double MIN_GAIN = 0.5;
    private static final double KEPT_VOTE = 0.05;
    private static final double INDIFFERENT = 1e-6;
    private static final double STRONG_PREFERENCE = 8;
    private static final String ABSTAIN = "";

    record Choice(String outcome, int votes) {}

    private record Option(String outcome, int votes, double value) {}

    static Optional<Choice> choose(Game game, Player seat, String agenda, List<Integer> planetVotes) {
        int available = planetVotes.stream().mapToInt(Integer::intValue).sum();
        if (available <= 0) return Optional.empty();
        boolean finalAgenda = AgendaRound.isFinalAgenda(game);
        int cap = AgendaRound.keepsAVoteBack(game) ? available - reserve(planetVotes) : available;
        if (cap <= 0) return Optional.empty();
        List<String> outcomes = AgendaOutcomes.of(game);
        if (outcomes.isEmpty()) return Optional.empty();
        AgendaForecast forecast = AgendaForecast.of(game, seat);
        if (forecast.isEmpty()) return withoutForecast(game, seat, agenda, outcomes, cap, finalAgenda);
        List<Integer> totals = reachableTotals(planetVotes, cap);
        ToDoubleFunction<Option> worth = option -> expectedValue(game, seat, agenda, forecast, option)
                - GalacticThreatCost.ofBallot(game, seat, option.outcome(), option.votes());
        double abstain = expectedValue(game, seat, agenda, forecast, new Option(ABSTAIN, 0, 0));
        if (finalAgenda && !AgendaValues.dependsOnOwnVote(agenda)) {
            return allIn(forecast, seat, outcomes, cap, worth, abstain);
        }
        double keptVote = finalAgenda ? 0 : KEPT_VOTE;
        Optional<Option> best = options(outcomes, totals).stream()
                .map(option -> new Option(
                        option.outcome(), option.votes(), worth.applyAsDouble(option) - keptVote * option.votes()))
                .max(Comparator.comparingDouble(Option::value)
                        .thenComparingInt(option -> finalAgenda ? option.votes() : -option.votes()));
        double needed = finalAgenda ? INDIFFERENT : MIN_GAIN;
        return best.filter(option -> option.value() - abstain > needed)
                .map(option -> new Choice(option.outcome(), option.votes()));
    }

    private static Optional<Choice> allIn(
            AgendaForecast forecast,
            Player seat,
            List<String> outcomes,
            int cap,
            ToDoubleFunction<Option> worth,
            double abstain) {
        double most = outcomes.stream()
                .mapToDouble(outcome -> forecast.utility(seat, outcome))
                .max()
                .orElse(0);
        double least = outcomes.stream()
                .mapToDouble(outcome -> forecast.utility(seat, outcome))
                .min()
                .orElse(0);
        if (most - least <= INDIFFERENT && most <= INDIFFERENT) return Optional.empty();
        return outcomes.stream()
                .map(outcome -> new Option(outcome, cap, 0))
                .max(Comparator.comparingDouble(worth)
                        .thenComparingDouble(option -> forecast.utility(seat, option.outcome())))
                .filter(option -> worth.applyAsDouble(option) >= abstain - INDIFFERENT)
                .map(option -> new Choice(option.outcome(), cap));
    }

    private static Optional<Choice> withoutForecast(
            Game game, Player seat, String agenda, List<String> outcomes, int cap, boolean finalAgenda) {
        ToDoubleFunction<String> value = outcome -> AgendaUtility.of(game, seat, true, agenda, outcome)
                - GalacticThreatCost.ofBallot(game, seat, outcome, cap);
        double least = outcomes.stream().mapToDouble(value).min().orElse(0);
        Optional<String> best = outcomes.stream().max(Comparator.comparingDouble(value));
        if (best.isEmpty()) return Optional.empty();
        double preference = value.applyAsDouble(best.get()) - least;
        if (preference <= INDIFFERENT && value.applyAsDouble(best.get()) <= INDIFFERENT) return Optional.empty();
        int votes = finalAgenda || preference >= STRONG_PREFERENCE ? cap : (cap + 1) / 2;
        return Optional.of(new Choice(best.get(), votes));
    }

    private static double expectedValue(Game game, Player seat, String agenda, AgendaForecast forecast, Option option) {
        AgendaForecast cast = option.outcome().isEmpty()
                ? forecast.without(seat)
                : forecast.withVotes(seat, option.outcome(), option.votes());
        return cast.expected(winner -> forecast.utility(seat, winner)
                + AgendaValues.ownVoteAdjustment(game, seat, agenda, winner, option.outcome()));
    }

    private static List<Option> options(List<String> outcomes, List<Integer> totals) {
        List<Option> options = new ArrayList<>();
        for (String outcome : outcomes) {
            for (int votes : totals) options.add(new Option(outcome, votes, 0));
        }
        return options;
    }

    static int reserve(List<Integer> planetVotes) {
        return planetVotes.stream()
                .mapToInt(Integer::intValue)
                .filter(votes -> votes > 0)
                .min()
                .orElse(0);
    }

    static List<Integer> reachableTotals(List<Integer> planetVotes, int cap) {
        boolean[] reachable = new boolean[cap + 1];
        reachable[0] = true;
        for (int votes : planetVotes) {
            if (votes <= 0) continue;
            for (int total = cap; total >= votes; total--) {
                if (reachable[total - votes]) reachable[total] = true;
            }
        }
        List<Integer> totals = new ArrayList<>();
        for (int total = 1; total <= cap; total++) {
            if (reachable[total]) totals.add(total);
        }
        return totals;
    }
}
