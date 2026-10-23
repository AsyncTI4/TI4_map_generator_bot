package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalPlanner.Candidate;
import ti4.ai.tactical.TacticalPlanner.Context;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
class SharedSystemAttack {

    private record FollowOn(Player opponent, StrikeEstimate estimate, double worth) {}

    static Optional<TacticalPlan> best(Context context, Tile tile) {
        List<Player> present = TacticalPlanner.othersIn(context.game, context.seat, tile);
        if (present.size() < 2 || present.stream().anyMatch(other -> !other.isRealPlayer() && !other.isNeutral())) {
            return Optional.empty();
        }
        List<Player> withShips = present.stream()
                .filter(other -> !BoardView.ships(BoardView.space(tile), other).isEmpty())
                .toList();
        if (withShips.size() > 1) return Optional.empty();
        TacticalPlan best = null;
        for (Player lead : withShips.isEmpty() ? present : withShips) {
            Optional<TacticalPlan> plan = TacticalPlanner.finalPlan(
                    TacticalPlanner.candidatePool(context, tile, lead),
                    finalist -> plan(context, tile, present, finalist));
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    private static Optional<TacticalPlan> plan(Context context, Tile tile, List<Player> present, Candidate lead) {
        StrikeEstimate estimate = lead.estimate();
        if (estimate.spaceWin() < lead.strike().rules().landMinWin()) return Optional.empty();
        double score = context.offense()
                .evaluate(lead.strike(), estimate, lead.terms(), true)
                .score();
        Map<String, Integer> landings = new LinkedHashMap<>(estimate.landings());
        Map<String, Double> wins = new LinkedHashMap<>(estimate.groundWins());
        Map<String, Integer> sent = new LinkedHashMap<>(estimate.groundSent());
        Map<UnitType, Integer> left = StrikeEvaluator.cargoLeft(context.seat, lead.strike(), estimate);
        List<Player> undecided = new ArrayList<>(present);
        undecided.remove(lead.strike().opponent());
        while (!undecided.isEmpty() && count(left) > 0) {
            Optional<FollowOn> next = bestFollowOn(context, tile, lead, undecided, left, landings.keySet());
            if (next.isEmpty()) break;
            StrikeEstimate taken = next.get().estimate();
            landings.putAll(taken.landings());
            wins.putAll(taken.groundWins());
            sent.putAll(taken.groundSent());
            takeUnits(
                    left,
                    taken.groundSent().values().stream()
                            .mapToInt(Integer::intValue)
                            .sum());
            score += next.get().worth();
            undecided.remove(next.get().opponent());
        }
        List<UnitMove> moves = lead.moves();
        if (landings.size() > estimate.landings().size()) {
            Optional<List<UnitMove>> legal = TacticalPlanner.executedMoves(
                    context, lead.parts(), tile, withLandings(estimate, landings, wins, sent));
            if (legal.isEmpty()) return Optional.empty();
            moves = legal.get();
        }
        score -= CapacityUse.loneHaulerPenalty(context.seat, moves);
        if (score < TacticalPlanner.ATTACK_MIN_SCORE) return Optional.empty();
        return Optional.of(new TacticalPlan(Kind.ATTACK, tile.getPosition(), moves, landings, score));
    }

    private static Optional<FollowOn> bestFollowOn(
            Context context,
            Tile tile,
            Candidate lead,
            List<Player> undecided,
            Map<UnitType, Integer> left,
            Set<String> landed) {
        FollowOn best = null;
        for (Player opponent : undecided) {
            Optional<FollowOn> found = followOn(context, tile, lead, opponent, left, landed);
            if (found.isPresent() && (best == null || found.get().worth() > best.worth())) best = found.get();
        }
        return best == null || best.worth() <= 0 ? Optional.empty() : Optional.of(best);
    }

    private static Optional<FollowOn> followOn(
            Context context,
            Tile tile,
            Candidate lead,
            Player opponent,
            Map<UnitType, Integer> left,
            Set<String> landed) {
        StrikeRules rules = TacticalPlanner.rules(
                context, tile, opponent, lead.strike().rules().homeLandings());
        Strike strike = new Strike(
                tile,
                opponent,
                List.of(),
                List.of(),
                lead.estimate().survivors(),
                Map.of(),
                new EnumMap<>(left),
                Map.of(),
                Map.of(),
                rules,
                Map.of());
        StrikeEstimate estimate = StrikeEvaluator.afterSpaceWon(
                context.game, context.seat, strike, lead.estimate().spaceWin());
        if (estimate.landings().isEmpty()) return Optional.empty();
        Set<String> together = new HashSet<>(landed);
        together.addAll(estimate.landings().keySet());
        double objective =
                context.objectiveGain(tile, lead.moves(), together) - context.objectiveGain(tile, lead.moves(), landed);
        ContextTerms terms = new ContextTerms(
                objective, Map.of(), 0, 0, 0, 0, 0, TacticalPlanner.territoryValues(context, tile), 0, 0);
        StrikeValue value = context.offense().evaluate(strike, estimate, terms, true);
        return Optional.of(new FollowOn(opponent, estimate, value.score() + value.counter() - value.defense()));
    }

    private static StrikeEstimate withLandings(
            StrikeEstimate estimate,
            Map<String, Integer> landings,
            Map<String, Double> wins,
            Map<String, Integer> sent) {
        return new StrikeEstimate(
                estimate.spaceWin(),
                estimate.escaped(),
                estimate.spaceCombat(),
                estimate.groundCombat(),
                landings,
                wins,
                sent,
                estimate.ourExpectedLoss(),
                estimate.ourLossIfWin(),
                estimate.theirExpectedLoss(),
                estimate.spaceKillChance(),
                estimate.groundKillChance(),
                estimate.ourLossChance(),
                estimate.survivors());
    }

    private static int count(Map<UnitType, Integer> units) {
        return units.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void takeUnits(Map<UnitType, Integer> units, int wanted) {
        int left = wanted;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int taken = Math.min(left, units.getOrDefault(type, 0));
            if (taken > 0) units.merge(type, -taken, Integer::sum);
            left -= taken;
        }
    }
}
