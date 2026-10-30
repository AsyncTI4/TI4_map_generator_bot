package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;

@UtilityClass
class WarfareUses {

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        boolean thundersEdge = CardVariant.of(in.game(), initiative) == CardVariant.THUNDERS_EDGE;
        Tile home = in.seat().getHomeSystemTile();
        Optional<BuildPlan> firstBuild = firstBuild(in, home);
        double planFromHome = home == null ? 0 : planFromHome(in, home);
        String homePosition = home == null ? "" : home.getPosition();
        List<Benefit> options = new ArrayList<>();
        options.add(new Benefit(CardUse.WARFARE_DOUBLE_BUILD, doubleBuild(in, home, firstBuild), homePosition));
        options.add(new Benefit(
                CardUse.WARFARE_BUILD_THEN_MOVE, buildThenMove(firstBuild, planFromHome, thundersEdge), homePosition));
        options.add(farReach(in));
        unblock(in).ifPresent(options::add);
        if (thundersEdge) extraAction(in).ifPresent(options::add);
        options.add(new Benefit(
                CardUse.OPENING_EXPANSION,
                OpeningOutlook.gain(in.game(), in.seat(), OpeningOutlook.Boost.EXTRA_ACTION)));
        Benefit best =
                options.stream().max(Comparator.comparingDouble(Benefit::value)).orElseThrow();
        if (thundersEdge) return List.of(best);
        return List.of(new Benefit(CardUse.WARFARE_TOKEN, TokenValue.tacticTokens(in, 1)), best);
    }

    static Optional<String> bestTokenToRemove(PlanInputs in, List<String> offered) {
        if (offered.isEmpty()) return Optional.empty();
        Tile home = in.seat().getHomeSystemTile();
        String homePosition = home == null ? "" : home.getPosition();
        String best = offered.getFirst();
        double bestGain = in.unblockGain(best);
        for (String position : offered) {
            double gain = in.unblockGain(position);
            if (gain > bestGain || (gain == bestGain && position.equals(homePosition))) {
                best = position;
                bestGain = gain;
            }
        }
        return Optional.of(best);
    }

    static double bestUnblockGain(PlanInputs in) {
        return unblock(in).map(Benefit::value).orElse(0.0);
    }

    private static Optional<BuildPlan> firstBuild(PlanInputs in, Tile home) {
        if (home == null || !TacticalPlanner.worthProducingAt(in.game(), in.seat(), home)) return Optional.empty();
        BuildPlan plan = ProductionPlanner.plan(in.game(), in.seat(), home);
        return plan.isEmpty() ? Optional.empty() : Optional.of(plan);
    }

    private static double doubleBuild(PlanInputs in, Tile home, Optional<BuildPlan> firstBuild) {
        if (firstBuild.isEmpty()) return 0;
        Game game = in.game();
        Player seat = in.seat();
        int spent = (int) Math.ceil(firstBuild.get().totalCost());
        double second = ProductionPlanner.plan(game, seat, home, -spent).value() * CardValueScale.PRODUCTION_SHARE;
        int fleetRoom = FleetLimit.ships(seat)
                - BoardView.nonFighterShips(BoardView.space(home), seat)
                - nonFighterShips(firstBuild.get());
        return fleetRoom <= 0 ? second * CardValueScale.CROWDED_DOUBLE_BUILD_SHARE : second;
    }

    private static int nonFighterShips(BuildPlan plan) {
        return BoardView.MOVING_SHIPS.stream().mapToInt(plan::units).sum();
    }

    private static double buildThenMove(Optional<BuildPlan> firstBuild, double planFromHome, boolean thundersEdge) {
        if (firstBuild.isEmpty()) return 0;
        double built = firstBuild.get().value();
        if (thundersEdge) {
            return CardValueScale.PRODUCTION_SHARE * built * CardValueScale.TE_FIRST_BUILD_SHARE
                    + CardValueScale.BUILD_THEN_MOVE_SHARE * planFromHome;
        }
        return CardValueScale.BUILD_THEN_MOVE_SHARE * Math.min(built, planFromHome);
    }

    private static double planFromHome(PlanInputs in, Tile home) {
        List<TacticalPlan> plans = home.hasPlayerCC(in.seat()) ? in.rankedFreeing(home.getPosition()) : in.ranked();
        return TacticalPlanner.bestScore(ConstructionUses.movingOutOf(plans, home.getPosition()));
    }

    private static Benefit farReach(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        Benefit best = new Benefit(CardUse.WARFARE_FAR_REACH, 0);
        for (Tile origin : game.getTileMap().values()) {
            UnitHolder space = BoardView.space(origin);
            if (BoardView.nonFighterShips(space, seat) <= 0) continue;
            int move = BoardView.ships(space, seat).keySet().stream()
                    .filter(BoardView.MOVING_SHIPS::contains)
                    .mapToInt(type -> BoardView.moveValueWithGravityDrive(seat, type))
                    .max()
                    .orElse(0);
            if (move <= 0 || !carriesGroundForces(seat, origin)) continue;
            Set<String> near =
                    MovementGraph.reach(game, seat, origin.getPosition(), move).keySet();
            Map<String, Integer> far = MovementGraph.reach(game, seat, origin.getPosition(), 2 * move);
            for (String position : far.keySet()) {
                if (near.contains(position)) continue;
                Tile tile = game.getTileByPosition(position);
                if (tile == null || BoardView.hasEnemyShips(game, seat, tile)) continue;
                double value = CardValueScale.FAR_REACH_SHARE * reachValue(in, tile);
                if (value > best.value()) best = new Benefit(CardUse.WARFARE_FAR_REACH, value, position);
            }
        }
        return best;
    }

    private static boolean carriesGroundForces(Player seat, Tile origin) {
        boolean capacity = BoardView.ships(BoardView.space(origin), seat).keySet().stream()
                .anyMatch(type -> BoardView.MOVING_SHIPS.contains(type) && BoardView.capacity(seat, type) > 0);
        return capacity
                && origin.getUnitHolders().values().stream()
                        .anyMatch(holder -> BoardView.groundForces(holder, seat) > 0);
    }

    private static double reachValue(PlanInputs in, Tile tile) {
        Game game = in.game();
        Player seat = in.seat();
        if (tile != game.getMecatolTile() || game.isCustodiansScored()) return uncontrolledValue(seat, tile);
        if (ScoringReserve.planForCustodians(game, seat).isEmpty()) return 0;
        return custodiansRaceGain(in);
    }

    private static double custodiansRaceGain(PlanInputs in) {
        CustodiansRace.Snapshot race = in.race();
        Player seat = in.seat();
        double thisRound = race.raceValue(seat, OptionalInt.of(in.game().getRound()), CustodiansRace.Order.UNKNOWN);
        double unaided = race.raceValue(seat, race.arrival(seat), CustodiansRace.Order.UNKNOWN);
        return Math.max(0, thisRound - unaided);
    }

    private static double uncontrolledValue(Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .mapToDouble(BoardView::planetValue)
                .sum();
    }

    private static Optional<Benefit> unblock(PlanInputs in) {
        if (in.moment() != PlanMoment.ACTION) return Optional.empty();
        Player seat = in.seat();
        return in.game().getTileMap().values().stream()
                .filter(tile -> tile.hasPlayerCC(seat))
                .map(tile -> new Benefit(
                        CardUse.WARFARE_UNBLOCK, Math.max(0, in.unblockGain(tile.getPosition())), tile.getPosition()))
                .max(Comparator.comparingDouble(Benefit::value));
    }

    private static Optional<Benefit> extraAction(PlanInputs in) {
        List<TacticalPlan> ranked = in.ranked();
        if (ranked.isEmpty()) return Optional.empty();
        int affordable = Math.max(0, in.seat().getTacticalCC());
        if (affordable >= ranked.size()) {
            return Optional.of(new Benefit(
                    CardUse.WARFARE_EXTRA_ACTION,
                    CardValueScale.TOKEN_FLOOR,
                    ranked.getFirst().target()));
        }
        TacticalPlan next = ranked.get(affordable);
        return Optional.of(new Benefit(
                CardUse.WARFARE_EXTRA_ACTION, Math.max(CardValueScale.TOKEN_FLOOR, next.score()), next.target()));
    }
}
