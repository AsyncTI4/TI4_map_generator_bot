package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.IconStake;
import ti4.ai.eval.MecatolStake;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.Territory;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.model.Mapper;

public final class RoundGoals {

    public record Goal(String position, double value, boolean needsTransport, int planets) {}

    private static final Set<Territory.Zone> OWN_AREA =
            Set.of(Territory.Zone.LANE, Territory.Zone.SLICE, Territory.Zone.BORDER);
    private static final String DARK_ENERGY_TAP = "det";
    private static final double NEXT_ACTIVATION_SHARE = 0.4;

    private final Game game;
    private final Player seat;
    private final List<Goal> goals;
    private final Map<String, Double> potentials = new HashMap<>();

    private RoundGoals(Game game, Player seat, List<Goal> goals) {
        this.game = game;
        this.seat = seat;
        this.goals = goals;
    }

    public static RoundGoals of(Game game, Player seat) {
        return of(game, seat, Territory.of(game, seat));
    }

    static RoundGoals of(Game game, Player seat, Territory territory) {
        List<Goal> goals = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            double claim = expansionValue(game, seat, territory, tile) + iconValue(game, seat, tile);
            double presence = mecatolValue(game, seat, tile) + frontierValue(seat, tile);
            if (claim > 0) goals.add(new Goal(tile.getPosition(), claim, true, claimablePlanets(game, tile)));
            if (presence > 0) goals.add(new Goal(tile.getPosition(), presence, false, 0));
        }
        return new RoundGoals(game, seat, List.copyOf(goals));
    }

    public List<Goal> goals() {
        return goals;
    }

    public double forwardGain(Tile from, Tile to, int move, boolean transports) {
        return Math.max(0, potential(to, move, to, transports) - potential(from, move, to, transports));
    }

    private double potential(Tile at, int move, Tile claimed, boolean transports) {
        if (move <= 0) return 0;
        return potentials.computeIfAbsent(
                at.getPosition() + "#" + move + "#" + claimed.getPosition() + "#" + transports,
                key -> computePotential(at, move, claimed, transports));
    }

    private double computePotential(Tile at, int move, Tile claimed, boolean transports) {
        Map<String, Integer> reach = MovementGraph.reach(game, seat, at.getPosition(), 2 * move);
        double potential = 0;
        for (Goal goal : goals) {
            if (goal.needsTransport() && !transports) continue;
            if (goal.position().equals(at.getPosition()) || goal.position().equals(claimed.getPosition())) continue;
            Integer distance = reach.get(goal.position());
            if (distance == null) continue;
            potential += goal.value() * (distance <= move ? 1 : NEXT_ACTIVATION_SHARE);
        }
        return potential;
    }

    private static double expansionValue(Game game, Player seat, Territory territory, Tile tile) {
        Territory.Zone zone = territory.zone(tile);
        if (!OWN_AREA.contains(zone) || BoardView.hasEnemyUnits(game, seat, tile)) return 0;
        double value = 0;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (BoardView.controller(game, planet.getName()) != null || BoardView.hasCustodians(planet)) continue;
            value += BoardView.planetValue(planet) + TacticalPlanner.ownAreaBonus(zone);
        }
        return value;
    }

    private static double iconValue(Game game, Player seat, Tile tile) {
        if (tile.isMecatol(game) || BoardView.hasEnemyUnits(game, seat, tile)) return 0;
        return IconStake.freeValue(game, seat, tile);
    }

    private static int claimablePlanets(Game game, Tile tile) {
        return (int) tile.getPlanetUnitHolders().stream()
                .filter(planet ->
                        BoardView.controller(game, planet.getName()) == null && !BoardView.hasCustodians(planet))
                .count();
    }

    private static double mecatolValue(Game game, Player seat, Tile tile) {
        if (!tile.isMecatol(game) || seat.controlsMecatol(true)) return 0;
        return MecatolStake.ownHoldValue(game, seat) + IconStake.freeValue(game, seat, tile);
    }

    private static double frontierValue(Player seat, Tile tile) {
        if (!seat.hasTech(DARK_ENERGY_TAP) || BoardView.hasOwnShips(seat, tile)) return 0;
        boolean frontier = BoardView.space(tile).getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER));
        return frontier ? TacticalPlanner.frontierCardValue(seat) : 0;
    }
}
