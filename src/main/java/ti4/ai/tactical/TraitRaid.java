package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.TraitNeeds;
import ti4.ai.eval.TraitNeeds.Need;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.TacticalPlanner.Candidate;
import ti4.ai.tactical.TacticalPlanner.Context;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class TraitRaid {

    public enum RaidGate {
        NO_TARGET,
        NOT_ACTIVATABLE,
        SHARED_SYSTEM,
        NO_FLEET,
        NO_CARGO,
        WIN_CHANCE,
        SCORE,
        OPEN
    }

    public enum ClaimGate {
        NOT_ACTIVATABLE,
        NO_REACH,
        SPACE_CANNON,
        ENEMY_IN_SYSTEM,
        EXPANDABLE
    }

    private static final int SHORTEST_RAIDABLE = 1;
    private static final int LONGEST_RAIDABLE = 2;
    private static final int RAID_CARGO = 3;
    private static final int RAID_RANGE_MOVES = 2;

    static double holdLoss(Game game, Player seat, Planet planet) {
        boolean oneShort =
                TraitNeeds.of(game, seat).stream().anyMatch(need -> need.missing() == 1 && need.wants(planet));
        return oneShort ? DefenseOutlook.SCORING_CRITICAL : 0;
    }

    static double exposedObjective(Context context, Tile tile, Strike strike, StrikeEstimate estimate) {
        Player opponent = strike.opponent();
        if (opponent.isNeutral() || !landsOnPlanetOf(context.game, estimate, opponent)) return 0;
        double gain = context.objectives()
                .traitSecretGain(context.footprintAfter(
                        tile, strike.moves(), estimate.landings().keySet()));
        return Math.max(0, gain);
    }

    static int infantryToBuild(Game game, Player seat, Tile dock, int spare, DefenseOutlook defense) {
        int wanted = RAID_CARGO - spare;
        if (wanted <= 0) return 0;
        List<Need> needs = raidable(game, seat);
        if (needs.isEmpty() || freeCount(game, needs) >= missing(needs)) return 0;
        return raidTargetWithin(game, seat, dock, needs, defense) ? wanted : 0;
    }

    public static String firstBlockingGate(Game game, Player seat) {
        List<Need> needs = TraitNeeds.of(game, seat).stream()
                .filter(need -> need.missing() == 1)
                .toList();
        if (needs.isEmpty()) return "";
        Context context = new Context(game, seat);
        RaidGate furthest = RaidGate.NO_TARGET;
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                Player owner = BoardView.controller(game, planet.getName());
                if (owner == null || owner == seat || !TraitNeeds.wanted(needs, planet)) continue;
                RaidGate gate = gateFor(context, tile, planet);
                if (gate.compareTo(furthest) > 0) furthest = gate;
            }
        }
        return furthest.name();
    }

    public static String firstUnclaimedGate(Game game, Player seat) {
        List<Need> needs = TraitNeeds.of(game, seat);
        if (needs.isEmpty()) return "";
        Context context = new Context(game, seat);
        ClaimGate furthest = null;
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (BoardView.controller(game, planet.getName()) != null
                        || BoardView.hasCustodians(planet)
                        || !TraitNeeds.wanted(needs, planet)) {
                    continue;
                }
                ClaimGate gate = claimGate(context, tile);
                if (furthest == null || gate.compareTo(furthest) > 0) furthest = gate;
            }
        }
        return furthest == null ? "" : furthest.name();
    }

    private static ClaimGate claimGate(Context context, Tile tile) {
        if (!TacticalPlanner.activatable(context.game, context.seat, tile)) return ClaimGate.NOT_ACTIVATABLE;
        if (context.expansionPlan(tile).isPresent()) return ClaimGate.EXPANDABLE;
        if (BoardView.hasEnemyUnits(context.game, context.seat, tile)) return ClaimGate.ENEMY_IN_SYSTEM;
        if (!context.cannonChances(tile).isEmpty()) return ClaimGate.SPACE_CANNON;
        return ClaimGate.NO_REACH;
    }

    private static RaidGate gateFor(Context context, Tile tile, Planet planet) {
        Game game = context.game;
        Player seat = context.seat;
        if (!TacticalPlanner.activatable(game, seat, tile)) return RaidGate.NOT_ACTIVATABLE;
        Player opponent = TacticalPlanner.singleOpponent(game, seat, tile);
        if (opponent == null) return RaidGate.SHARED_SYSTEM;
        Optional<Candidate> best = TacticalPlanner.candidatePool(context, tile, opponent).stream()
                .max(Comparator.comparingDouble(found -> found.quick().score()));
        if (best.isEmpty()) return RaidGate.NO_FLEET;
        Candidate candidate = best.get();
        StrikeEstimate estimate = candidate.estimate();
        if (estimate.groundSent().isEmpty()) return RaidGate.NO_CARGO;
        if (!estimate.landings().containsKey(planet.getName())) return RaidGate.WIN_CHANCE;
        return TacticalPlanner.fullPlan(context, tile, candidate).isPresent() ? RaidGate.OPEN : RaidGate.SCORE;
    }

    private static List<Need> raidable(Game game, Player seat) {
        return TraitNeeds.of(game, seat).stream()
                .filter(need -> need.missing() >= SHORTEST_RAIDABLE && need.missing() <= LONGEST_RAIDABLE)
                .toList();
    }

    private static int missing(List<Need> needs) {
        return needs.stream().mapToInt(Need::missing).min().orElse(0);
    }

    private static int freeCount(Game game, List<Need> needs) {
        int free = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                boolean claimable = BoardView.controller(game, planet.getName()) == null
                        && !BoardView.hasCustodians(planet)
                        && ObjectiveValue.countsForScoring(game, planet);
                if (claimable && TraitNeeds.wanted(needs, planet)) free++;
            }
        }
        return free;
    }

    private static boolean heldBy(Game game, Tile tile, Player owner, List<Need> needs) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet ->
                        BoardView.controller(game, planet.getName()) == owner && TraitNeeds.wanted(needs, planet));
    }

    private static boolean landsOnPlanetOf(Game game, StrikeEstimate estimate, Player opponent) {
        return estimate.landings().keySet().stream().anyMatch(name -> BoardView.controller(game, name) == opponent);
    }

    private static boolean raidTargetWithin(
            Game game, Player seat, Tile dock, List<Need> needs, DefenseOutlook defense) {
        int move = BoardView.moveValueWithBoosts(seat, UnitType.Carrier);
        Map<String, Integer> reach = MovementGraph.reach(game, seat, dock.getPosition(), RAID_RANGE_MOVES * move);
        for (Tile tile : game.getTileMap().values()) {
            if (!reach.containsKey(tile.getPosition())) continue;
            for (Player owner : game.getRealPlayers()) {
                boolean target = owner != seat && heldBy(game, tile, owner, needs);
                if (target && permitted(game, seat, defense, owner)) return true;
            }
        }
        return false;
    }

    private static boolean permitted(Game game, Player seat, DefenseOutlook defense, Player opponent) {
        return !soleLeader(game, opponent) && !threatensHome(seat, defense, opponent);
    }

    private static boolean soleLeader(Game game, Player opponent) {
        return game.getRealPlayers().stream()
                .filter(player -> player != opponent)
                .allMatch(player -> player.getTotalVictoryPoints() < opponent.getTotalVictoryPoints());
    }

    private static boolean threatensHome(Player seat, DefenseOutlook defense, Player opponent) {
        Tile home = seat.getHomeSystemTile();
        if (home == null) return false;
        double reach = defense.force(opponent, home, Horizon.NEXT_ROUND).fleetCost();
        return reach > 0 && reach >= defense.fleetCostTotal(seat);
    }
}
