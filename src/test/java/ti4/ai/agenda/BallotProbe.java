package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ti4.ai.trace.Thinking;
import ti4.ai.trace.ThinkingLimits;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;

// Test-side window on how BallotPlanner sees one seat's ballot, for the self-play agenda trace. It rebuilds the
// planner's numbers from the seat's own forecast (which reads only that seat's own secrets): the utility per outcome
// the seat voted with, the forecast win chance of each outcome if it abstains and with the ballot it chose, the
// planner's value of abstaining, of its ballot and of smaller ballots for the same outcome. Measurement only.
public final class BallotProbe {

    // Mirrors of BallotPlanner's private constants, so the trace shows the margins the planner applied.
    static final double MIN_GAIN = 0.5;
    static final double KEPT_VOTE = 0.05;

    public record Explanation(
            String planned,
            int plannedVotes,
            boolean promised,
            int available,
            int cap,
            boolean keepsAVoteBack,
            boolean finalAgenda,
            String sitsOut,
            Map<String, Double> utility,
            Map<String, Double> chanceIfAbstain,
            Map<String, Double> chanceWithBallot,
            double abstainValue,
            double ballotValue,
            double threatCost,
            String bestOutcome,
            int bestVotes,
            double bestValue,
            List<String> curve) {}

    private BallotProbe() {}

    // Each probe runs in its own unlimited thinking session: called from inside a seat's decision, the planner's spans
    // would otherwise count against that decision's call cap and budget, abort it and stall the seat.
    public static Optional<Explanation> explain(Game game, Player seat) {
        try (Thinking.Decision isolated = Thinking.open(ThinkingLimits.unlimited())) {
            return explainIsolated(game, seat);
        }
    }

    private static Optional<Explanation> explainIsolated(Game game, Player seat) {
        String agenda = AgendaPolicy.currentAgenda(game);
        List<String> outcomes = AgendaOutcomes.of(game);
        if (agenda.isBlank() || outcomes.isEmpty()) return Optional.empty();
        List<Integer> planetVotes = seat.getReadiedPlanets().stream()
                .map(planet -> AgendaHelper.getSpecificPlanetsVoteWorth(seat, game, planet))
                .toList();
        int available = planetVotes.stream().mapToInt(Integer::intValue).sum();
        boolean keepsBack = AgendaRound.keepsAVoteBack(game);
        boolean finalAgenda = AgendaRound.isFinalAgenda(game);
        int cap = Math.max(0, keepsBack ? available - BallotPlanner.reserve(planetVotes) : available);
        String sitsOut = AgendaVoting.cannotVote(game, seat)
                ? "cannot vote (Galactic Threat)"
                : AgendaVoting.castableVotes(game, seat) == 0 && cap > 0
                        ? "sits out (assassinated or placed a rider)"
                        : "";
        Optional<AgendaVoting.Ballot> plan = AgendaVoting.plan(game, seat);
        boolean promised = PromisedVotes.binding(game, seat).isPresent();
        AgendaForecast forecast = AgendaForecast.of(game, seat);
        Map<String, Double> utility = new LinkedHashMap<>();
        for (String outcome : outcomes)
            utility.put(
                    outcome,
                    forecast.isEmpty()
                            ? AgendaUtility.of(game, seat, true, agenda, outcome)
                            : forecast.utility(seat, outcome));
        if (forecast.isEmpty()) {
            return Optional.of(new Explanation(
                    plan.map(AgendaVoting.Ballot::outcome).orElse(""),
                    plan.map(AgendaVoting.Ballot::votes).orElse(0),
                    promised,
                    available,
                    cap,
                    keepsBack,
                    finalAgenda,
                    sitsOut,
                    utility,
                    Map.of(),
                    Map.of(),
                    0,
                    0,
                    0,
                    "",
                    0,
                    0,
                    List.of()));
        }
        Map<String, Double> ifAbstain =
                new LinkedHashMap<>(forecast.without(seat).chances());
        double abstain = value(game, seat, agenda, forecast, "", 0);
        String planned = plan.map(AgendaVoting.Ballot::outcome).orElse("");
        int plannedVotes = plan.map(AgendaVoting.Ballot::votes).orElse(0);
        Map<String, Double> withBallot = planned.isBlank()
                ? ifAbstain
                : new LinkedHashMap<>(
                        forecast.withVotes(seat, planned, plannedVotes).chances());
        double ballot = planned.isBlank() ? abstain : value(game, seat, agenda, forecast, planned, plannedVotes);
        double threat = planned.isBlank() ? 0 : GalacticThreatCost.ofBallot(game, seat, planned, plannedVotes);
        List<Integer> totals = cap <= 0 ? List.of() : BallotPlanner.reachableTotals(planetVotes, cap);
        double keptVote = finalAgenda ? 0 : KEPT_VOTE;
        String bestOutcome = "";
        int bestVotes = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (String outcome : outcomes) {
            for (int votes : totals) {
                double worth = value(game, seat, agenda, forecast, outcome, votes)
                        - GalacticThreatCost.ofBallot(game, seat, outcome, votes)
                        - keptVote * votes;
                if (worth > bestValue + 1e-9) {
                    bestValue = worth;
                    bestOutcome = outcome;
                    bestVotes = votes;
                }
            }
        }
        List<String> curve = new ArrayList<>();
        String curveOutcome = planned.isBlank() ? bestOutcome : planned;
        if (!curveOutcome.isBlank()) {
            for (int votes : sampled(totals)) {
                double worth = value(game, seat, agenda, forecast, curveOutcome, votes)
                        - GalacticThreatCost.ofBallot(game, seat, curveOutcome, votes)
                        - keptVote * votes;
                double chance = forecast.withVotes(seat, curveOutcome, votes).chance(curveOutcome);
                curve.add(String.format("%d:%+.2f(%.0f%%)", votes, worth - abstain, 100 * chance));
            }
        }
        return Optional.of(new Explanation(
                planned,
                plannedVotes,
                promised,
                available,
                cap,
                keepsBack,
                finalAgenda,
                sitsOut,
                utility,
                ifAbstain,
                withBallot,
                abstain,
                ballot - threat - keptVote * plannedVotes,
                threat,
                bestOutcome,
                bestVotes,
                bestValue == Double.NEGATIVE_INFINITY ? 0 : bestValue,
                curve));
    }

    public static boolean cannotVote(Game game, Player seat) {
        return AgendaVoting.cannotVote(game, seat);
    }

    public static Map<String, Double> chances(Game game, Player seat) {
        try (Thinking.Decision isolated = Thinking.open(ThinkingLimits.unlimited())) {
            AgendaForecast forecast = AgendaForecast.of(game, seat);
            return forecast.isEmpty() ? Map.of() : new LinkedHashMap<>(forecast.chances());
        }
    }

    public static double riderChance(Game game, Player owner, String outcome) {
        try (Thinking.Decision isolated = Thinking.open(ThinkingLimits.unlimited())) {
            AgendaForecast forecast = AgendaForecast.of(game, owner);
            if (forecast.isEmpty()) return Double.NaN;
            for (Map.Entry<String, Double> entry : forecast.chances().entrySet())
                if (entry.getKey().equalsIgnoreCase(outcome)) return entry.getValue();
            return 0;
        }
    }

    private static List<Integer> sampled(List<Integer> totals) {
        if (totals.size() <= 8) return totals;
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int index = Math.round(i * (totals.size() - 1) / 7f);
            if (!picked.contains(totals.get(index))) picked.add(totals.get(index));
        }
        return picked;
    }

    // BallotPlanner.expectedValue: the seat's utility of each winner, weighted by the forecast with this ballot cast.
    private static double value(
            Game game, Player seat, String agenda, AgendaForecast forecast, String outcome, int votes) {
        AgendaForecast cast = outcome.isEmpty() ? forecast.without(seat) : forecast.withVotes(seat, outcome, votes);
        return cast.expected(winner ->
                forecast.utility(seat, winner) + AgendaValues.ownVoteAdjustment(game, seat, agenda, winner, outcome));
    }
}
