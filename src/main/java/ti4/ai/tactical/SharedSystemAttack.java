package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
class SharedSystemAttack {

    private static final int MAX_FLEETS = 2;

    private record FollowOn(Player opponent, StrikeEstimate estimate, double worth) {}

    private record Aftermath(Map<UnitType, Integer> survivors, double spaceWin) {}

    private record SecondFight(Strike strike, StrikeEstimate estimate, StrikeValue value, double worth) {}

    private static final class Landings {
        final Map<String, Integer> planets = new LinkedHashMap<>();
        final Map<String, Double> wins = new LinkedHashMap<>();
        final Map<String, Integer> sent = new LinkedHashMap<>();

        void add(StrikeEstimate estimate) {
            planets.putAll(estimate.landings());
            wins.putAll(estimate.groundWins());
            sent.putAll(estimate.groundSent());
        }

        int units() {
            return sent.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    static Optional<TacticalPlan> best(Context context, Tile tile) {
        List<Player> present = TacticalPlanner.othersIn(context.game, context.seat, tile);
        if (present.size() < 2 || present.stream().anyMatch(other -> !other.isRealPlayer() && !other.isNeutral())) {
            return Optional.empty();
        }
        List<Player> withShips = present.stream()
                .filter(other -> !BoardView.ships(BoardView.space(tile), other).isEmpty())
                .toList();
        if (withShips.size() > MAX_FLEETS) return Optional.empty();
        if (withShips.size() == MAX_FLEETS)
            return twoFleets(context, tile, present, combatOrder(context.game, withShips));
        TacticalPlan best = null;
        for (Player lead : withShips.isEmpty() ? present : withShips) {
            Optional<TacticalPlan> plan = TacticalPlanner.finalPlan(
                    TacticalPlanner.candidatePool(context, tile, lead),
                    finalist -> plan(context, tile, present, finalist));
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    static List<Player> combatOrder(Game game, List<Player> fleets) {
        List<Player> seats = game.getRealPlayersNDummies();
        return fleets.stream()
                .sorted(Comparator.comparingInt(fleet -> seats.contains(fleet) ? seats.indexOf(fleet) : seats.size()))
                .toList();
    }

    private static Optional<TacticalPlan> plan(Context context, Tile tile, List<Player> present, Candidate lead) {
        StrikeEstimate estimate = lead.estimate();
        if (estimate.spaceWin() < lead.strike().rules().landMinWin()) return Optional.empty();
        double score = context.offense()
                .evaluate(lead.strike(), estimate, lead.terms(), true)
                .score();
        Landings landings = new Landings();
        landings.add(estimate);
        List<Player> undecided = new ArrayList<>(present);
        undecided.remove(lead.strike().opponent());
        score += followOns(
                context,
                tile,
                lead,
                new Aftermath(estimate.survivors(), estimate.spaceWin()),
                undecided,
                StrikeEvaluator.cargoLeft(context.seat, lead.strike(), estimate),
                landings);
        return finish(context, tile, lead, estimate.landings().size(), landings, score);
    }

    private static Optional<TacticalPlan> twoFleets(
            Context context, Tile tile, List<Player> present, List<Player> order) {
        Player second = order.get(1);
        return TacticalPlanner.finalPlan(
                TacticalPlanner.candidatePool(context, tile, order.getFirst()),
                finalist -> twoFleetPlan(context, tile, present, second, finalist));
    }

    private static Optional<TacticalPlan> twoFleetPlan(
            Context context, Tile tile, List<Player> present, Player second, Candidate lead) {
        Game game = context.game;
        Player seat = context.seat;
        Strike opening = withoutCargo(lead.strike());
        StrikeEstimate first = StrikeEvaluator.estimate(game, seat, opening);
        if (first.spaceWin() < lead.strike().rules().landMinWin()) return Optional.empty();
        StrikeValue openingValue = context.offense().evaluate(opening, first, spaceTerms(context, tile, lead), true);
        Map<UnitType, Integer> cargo = StrikeEvaluator.cargoLeft(seat, lead.strike(), first);
        Optional<SecondFight> fight = secondFight(context, tile, lead, second, first, cargo);
        if (fight.isEmpty()) return Optional.empty();
        StrikeEstimate later = fight.get().estimate();
        double won = first.spaceWin() * later.spaceWin();
        double score = openingValue.score()
                + openingValue.counter()
                + fight.get().worth()
                - sharedCounter(openingValue, first, fight.get().value(), later);
        Landings landings = new Landings();
        landings.add(later);
        List<Player> undecided = new ArrayList<>(present);
        undecided.remove(second);
        score += followOns(
                context,
                tile,
                lead,
                new Aftermath(later.survivors(), won),
                undecided,
                StrikeEvaluator.cargoLeft(seat, fight.get().strike(), later),
                landings);
        if (landings.units() > 0) {
            score -= (1 - first.spaceWin()) * StrikeEvaluator.shipCost(seat, firstUnits(cargo, landings.units()));
        }
        return finish(context, tile, lead, 0, landings, score);
    }

    private static Optional<SecondFight> secondFight(
            Context context,
            Tile tile,
            Candidate lead,
            Player second,
            StrikeEstimate first,
            Map<UnitType, Integer> cargo) {
        Optional<SecondFight> carrying = secondFight(context, tile, lead, second, first, cargo, true);
        Optional<SecondFight> spaceOnly = secondFight(context, tile, lead, second, first, cargo, false);
        if (carrying.isEmpty()) return spaceOnly;
        if (spaceOnly.isEmpty()) return carrying;
        return carrying.get().worth() >= spaceOnly.get().worth() ? carrying : spaceOnly;
    }

    private static Optional<SecondFight> secondFight(
            Context context,
            Tile tile,
            Candidate lead,
            Player second,
            StrikeEstimate first,
            Map<UnitType, Integer> cargo,
            boolean landing) {
        if (first.survivors().isEmpty()) return Optional.empty();
        Strike strike = new Strike(
                tile,
                second,
                List.of(),
                List.of(),
                first.survivors(),
                Map.of(),
                landing ? new EnumMap<>(cargo) : Map.of(),
                Map.of(),
                Map.of(),
                TacticalPlanner.rules(
                        context, tile, second, lead.strike().rules().homeLandings()),
                Map.of());
        StrikeEstimate estimate = StrikeEvaluator.laterSpaceCombat(context.game, context.seat, strike);
        if (landing && estimate.landings().isEmpty()) return Optional.empty();
        double objective =
                context.objectiveGain(tile, lead.moves(), estimate.landings().keySet())
                        - context.objectiveGain(tile, lead.moves(), Set.of());
        double[] secrets = TacticalPlanner.secretBonuses(context.game, context.seat, tile, strike, estimate);
        ContextTerms terms = new ContextTerms(
                objective,
                Map.of(),
                Math.max(0, secrets[0] - lead.terms().spaceSecretBonus()),
                secrets[1],
                0,
                0,
                0,
                TacticalPlanner.territoryValues(context, tile),
                0,
                0);
        StrikeValue value = context.offense().evaluate(strike, estimate, terms, true);
        double worth = first.spaceWin() * (value.score() + value.counter() - value.defense());
        return Optional.of(new SecondFight(strike, estimate, value, worth));
    }

    private static double sharedCounter(
            StrikeValue opening, StrikeEstimate first, StrikeValue later, StrikeEstimate second) {
        if (first.spaceWin() <= 0 || second.spaceWin() <= 0) return 0;
        double afterFirst = opening.counter() / first.spaceWin();
        double afterSecond = later.counter() / second.spaceWin();
        return first.spaceWin() * second.spaceWin() * Math.min(afterFirst, afterSecond);
    }

    private static ContextTerms spaceTerms(Context context, Tile tile, Candidate lead) {
        ContextTerms terms = lead.terms();
        return new ContextTerms(
                context.objectiveGain(tile, lead.moves(), Set.of()),
                Map.of(),
                terms.spaceSecretBonus(),
                0,
                terms.homeDefence(),
                0,
                0,
                Map.of(),
                0,
                terms.seatStake());
    }

    private static Strike withoutCargo(Strike strike) {
        return new Strike(
                strike.target(),
                strike.opponent(),
                strike.origins(),
                strike.moves(),
                strike.ships(),
                strike.damaged(),
                Map.of(),
                strike.extraDefenderShips(),
                strike.extraDefenderGround(),
                strike.rules(),
                Map.of());
    }

    private static Optional<TacticalPlan> finish(
            Context context, Tile tile, Candidate lead, int leadLandings, Landings landings, double score) {
        List<UnitMove> moves = lead.moves();
        if (landings.planets.size() > leadLandings || landings.units() != sentBy(lead.estimate())) {
            Optional<List<UnitMove>> legal = TacticalPlanner.executedMoves(
                    context,
                    lead.parts(),
                    tile,
                    withLandings(lead.estimate(), landings.planets, landings.wins, landings.sent));
            if (legal.isEmpty()) return Optional.empty();
            moves = legal.get();
        }
        double total = score - CapacityUse.loneHaulerPenalty(context.seat, moves);
        if (total < TacticalPlanner.ATTACK_MIN_SCORE) return Optional.empty();
        return Optional.of(new TacticalPlan(Kind.ATTACK, tile.getPosition(), moves, landings.planets, total));
    }

    private static int sentBy(StrikeEstimate estimate) {
        return estimate.groundSent().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private static double followOns(
            Context context,
            Tile tile,
            Candidate lead,
            Aftermath aftermath,
            List<Player> undecided,
            Map<UnitType, Integer> left,
            Landings landings) {
        double worth = 0;
        while (!undecided.isEmpty() && count(left) > 0) {
            Optional<FollowOn> next =
                    bestFollowOn(context, tile, lead, aftermath, undecided, left, landings.planets.keySet());
            if (next.isEmpty()) break;
            StrikeEstimate taken = next.get().estimate();
            landings.add(taken);
            takeUnits(left, sentBy(taken));
            worth += next.get().worth();
            undecided.remove(next.get().opponent());
        }
        return worth;
    }

    private static Optional<FollowOn> bestFollowOn(
            Context context,
            Tile tile,
            Candidate lead,
            Aftermath aftermath,
            List<Player> undecided,
            Map<UnitType, Integer> left,
            Set<String> landed) {
        FollowOn best = null;
        for (Player opponent : undecided) {
            Optional<FollowOn> found = followOn(context, tile, lead, aftermath, opponent, left, landed);
            if (found.isPresent() && (best == null || found.get().worth() > best.worth())) best = found.get();
        }
        return best == null || best.worth() <= 0 ? Optional.empty() : Optional.of(best);
    }

    private static Optional<FollowOn> followOn(
            Context context,
            Tile tile,
            Candidate lead,
            Aftermath aftermath,
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
                aftermath.survivors(),
                Map.of(),
                new EnumMap<>(left),
                Map.of(),
                Map.of(),
                rules,
                Map.of());
        StrikeEstimate estimate =
                StrikeEvaluator.afterSpaceWon(context.game, context.seat, strike, aftermath.spaceWin());
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

    private static Map<UnitType, Integer> firstUnits(Map<UnitType, Integer> units, int wanted) {
        Map<UnitType, Integer> taken = new EnumMap<>(UnitType.class);
        int left = wanted;
        for (UnitType type : BoardView.GROUND_FORCES) {
            int count = Math.min(left, units.getOrDefault(type, 0));
            if (count > 0) taken.put(type, count);
            left -= count;
        }
        return taken;
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
