package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class BallotPlanner {

    private static final double MIN_GAIN = 0.5;
    private static final double KEPT_VOTE = 0.05;
    private static final double NEXT_AGENDA_VOTE = 0.1;
    static final double LAST_VOTE = 0.5;
    private static final double NEAR_BEST = 0.3;
    private static final double INDIFFERENT = 1e-6;
    private static final double STRONG_PREFERENCE = 8;
    private static final String ABSTAIN = "";
    static final int PREDICTIVE_VOTES = 3;
    static final double PREDICTIVE_EXHAUSTED = 0.5;

    record Choice(String outcome, int votes, boolean predictive) {

        Choice(String outcome, int votes) {
            this(outcome, votes, false);
        }
    }

    record Option(String outcome, int votes, double value, boolean predictive) {

        Option(String outcome, int votes, double value) {
            this(outcome, votes, value, false);
        }

        int planetVotes() {
            return predictive ? votes - PREDICTIVE_VOTES : votes;
        }
    }

    static Optional<Choice> choose(Game game, Player seat, String agenda, List<Integer> planetVotes) {
        int available = planetVotes.stream().mapToInt(Integer::intValue).sum();
        if (available <= 0) return Optional.empty();
        boolean finalAgenda = AgendaRound.isFinalAgenda(game);
        int keep = AgendaRound.keepsAVoteBack(game) ? available - reserve(planetVotes) : available;
        boolean spendsTheLastVote = keep < available && AiExperiments.enabled(seat, Experiment.AGENDA_VALUE_TABLE);
        int cap = spendsTheLastVote ? available : keep;
        if (cap <= 0) return Optional.empty();
        List<String> outcomes = AgendaOutcomes.of(game);
        if (outcomes.isEmpty()) return Optional.empty();
        AgendaForecast forecast = AgendaForecast.of(game, seat);
        if (forecast.isEmpty()) {
            return keep <= 0 ? Optional.empty() : withoutForecast(game, seat, agenda, outcomes, keep, finalAgenda);
        }
        List<Integer> totals = reachableTotals(planetVotes, cap);
        boolean predictive = AgendaVoting.plansPredictive(seat);
        ToDoubleFunction<Option> worth = option -> expectedValue(game, seat, agenda, forecast, option)
                - GalacticThreatCost.ofBallot(game, seat, option.outcome(), option.votes());
        double abstain = expectedValue(game, seat, agenda, forecast, new Option(ABSTAIN, 0, 0));
        if (finalAgenda && !AgendaValues.dependsOnOwnVote(agenda)) {
            return allIn(forecast, seat, outcomes, cap, predictive, worth, abstain);
        }
        double keptVote = keptVote(game, seat);
        List<Option> valued = options(outcomes, totals, predictive).stream()
                .map(option -> new Option(
                        option.outcome(),
                        option.votes(),
                        worth.applyAsDouble(option)
                                - keptVote * option.planetVotes()
                                - (option.planetVotes() > keep ? LAST_VOTE : 0),
                        option.predictive()))
                .toList();
        Optional<Option> best = valued.stream()
                .max(Comparator.comparingDouble(Option::value)
                        .thenComparingInt(option -> finalAgenda ? option.votes() : -option.votes()));
        double needed = finalAgenda ? INDIFFERENT : MIN_GAIN;
        double floor = abstain + needed;
        return best.filter(option -> option.value() > floor)
                .map(option -> finalAgenda || !AiExperiments.enabled(seat, Experiment.AGENDA_VALUE_TABLE)
                        ? option
                        : fewestNearBest(valued, option, floor))
                .map(option -> new Choice(option.outcome(), option.votes(), option.predictive()));
    }

    static Option fewestNearBest(List<Option> valued, Option best, double floor) {
        return valued.stream()
                .filter(option -> option.outcome().equals(best.outcome()))
                .filter(option -> option.value() >= best.value() - NEAR_BEST)
                .filter(option -> option.value() > floor)
                .min(Comparator.comparingInt(Option::votes)
                        .thenComparing(Comparator.comparingDouble(Option::value).reversed()))
                .orElse(best);
    }

    private static Optional<Choice> allIn(
            AgendaForecast forecast,
            Player seat,
            List<String> outcomes,
            int cap,
            boolean predictive,
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
        List<Option> options = new ArrayList<>();
        for (String outcome : outcomes) {
            options.add(new Option(outcome, cap, 0));
            if (predictive) options.add(new Option(outcome, cap + PREDICTIVE_VOTES, 0, true));
        }
        return options.stream()
                .max(Comparator.comparingDouble(worth)
                        .thenComparingDouble(option -> forecast.utility(seat, option.outcome())))
                .filter(option -> worth.applyAsDouble(option) >= abstain - INDIFFERENT)
                .map(option -> new Choice(option.outcome(), option.votes(), option.predictive()));
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
        double value = cast.expected(winner -> forecast.utility(seat, winner)
                        + AgendaValues.ownVoteAdjustment(game, seat, agenda, winner, option.outcome()))
                - cast.threatPayoffCorrection(seat);
        if (!option.predictive()) return value;
        return value - PREDICTIVE_EXHAUSTED * (1 - cast.chance(option.outcome()));
    }

    private static List<Option> options(List<String> outcomes, List<Integer> totals, boolean predictive) {
        List<Option> options = new ArrayList<>();
        for (String outcome : outcomes) {
            for (int votes : totals) options.add(new Option(outcome, votes, 0));
            if (!predictive) continue;
            for (int votes : totals) options.add(new Option(outcome, votes + PREDICTIVE_VOTES, 0, true));
        }
        return options;
    }

    static double keptVote(Game game, Player seat) {
        if (AgendaRound.isFinalAgenda(game)) return 0;
        return AiExperiments.enabled(seat, Experiment.AGENDA_VALUE_TABLE) ? NEXT_AGENDA_VOTE : KEPT_VOTE;
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
