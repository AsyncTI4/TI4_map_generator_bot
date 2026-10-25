package ti4.ai.nekro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.strategy.TechPath;
import ti4.ai.strategy.TokenPlan;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;

@UtilityClass
public class CommandTokenPolicy {

    private static final int STATUS_PHASE_GAIN = 2;
    private static final int BASE_FLEET = 3;
    private static final int TACTIC_FIRST = 2;
    private static final int STRATEGY_TARGET = 2;
    private static final int TACTIC_PLENTY = 5;
    private static final int STRATEGY_MAX = 3;
    private static final int STACKING_FLEET_CAP = 5;
    private static final int BUILD_FLEET_MIN_CAPACITY = 4;
    private static final int BUILD_FLEET_MIN_RESOURCES = 6;
    private static final String RAISE_A_FLEET = "raise_fleet";
    private static final int RAISE_A_FLEET_SHIPS = 5;
    private static final String COMMAND_AN_ARMADA = "command_armada";
    private static final int ARMADA_SHIPS = 8;
    private static final int SHIPS_BUILT_PER_ROUND = 2;
    private static final String FLEET_REGULATIONS = "regulations";
    private static final int REGULATED_FLEET = 4;
    private static final String ACTION_PHASE = "action";

    public enum Pool {
        TACTIC("tactic"),
        FLEET("fleet"),
        STRATEGY("strategy");

        private final String id;

        Pool(String id) {
            this.id = id;
        }

        public String grow() {
            return "increase_" + id + "_cc";
        }

        public String shrink() {
            return "decrease_" + id + "_cc";
        }

        int count(Player seat) {
            return switch (this) {
                case TACTIC -> seat.getTacticalCC();
                case FLEET -> seat.getFleetCC();
                case STRATEGY -> seat.getStrategicCC();
            };
        }
    }

    private record Level(Pool pool, int count) {}

    public static int statusPhaseGain(Player seat) {
        int gain = STATUS_PHASE_GAIN;
        if (seat.hasAbility("versatile")) gain++;
        if (seat.hasTech("hm")) gain++;
        if (seat.hasTech("tf-inheritancesystems")) gain++;
        if (holdsOthersNote(seat, "ce")) gain++;
        if (holdsOthersNote(seat, "malevolency") && seat.getMahactCC().isEmpty()) gain--;
        return gain;
    }

    private static boolean holdsOthersNote(Player seat, String note) {
        return seat.getPromissoryNotes().containsKey(note) && !seat.ownsPromissoryNote(note);
    }

    public static int statusPhaseGainWithinReinforcements(Game game, Player seat) {
        return Math.min(statusPhaseGain(seat), gainWithinReinforcements(game, seat));
    }

    public static int gainWithinReinforcements(Game game, Player seat) {
        int reinforcementsBeforeGain =
                seat.getCommandTokenLimit() - Helper.getCCCount(game, seat.getColor()) + netGainSoFar(game, seat);
        return Math.max(0, reinforcementsBeforeGain);
    }

    public static int netGainSoFar(Game game, Player seat) {
        String original = game.getStoredValue("originalCCsFor" + seat.getFaction());
        if (original.isBlank()) return 0;
        return ButtonHelper.checkNetGain(seat, original);
    }

    public static String poolToGrow(Game game, Player seat) {
        for (Level level : priorities(game, seat)) {
            if (level.pool().count(seat) < level.count()) return level.pool().grow();
        }
        return Pool.TACTIC.grow();
    }

    public static Optional<String> poolToRedistributeFrom(Game game, Player seat) {
        Map<Pool, Integer> ideal = ideal(game, seat, totalTokens(seat));
        for (Pool pool : List.of(Pool.FLEET, Pool.STRATEGY, Pool.TACTIC)) {
            if (pool.count(seat) > ideal.get(pool)) return Optional.of(pool.shrink());
        }
        return Optional.empty();
    }

    public static List<String> poolsToShrink(Game game, Player seat) {
        Map<Pool, Integer> ideal = ideal(game, seat, Math.max(0, totalTokens(seat) - 1));
        int fleetFloor = Math.max(0, largestStack(game, seat) - fleetBonus(seat));
        List<Pool> order = new ArrayList<>(List.of(Pool.FLEET, Pool.STRATEGY, Pool.TACTIC));
        order.removeIf(pool -> pool.count(seat) <= 0);
        order.sort(Comparator.comparing((Pool pool) -> pool == Pool.FLEET && seat.getFleetCC() <= fleetFloor)
                .thenComparingInt(pool -> ideal.get(pool) - pool.count(seat)));
        return order.stream().map(Pool::shrink).toList();
    }

    public static Map<Pool, Integer> ideal(Game game, Player seat, int tokens) {
        Map<Pool, Integer> allocation = new EnumMap<>(Pool.class);
        for (Pool pool : Pool.values()) allocation.put(pool, 0);
        int remaining = tokens;
        for (Level level : priorities(game, seat)) {
            int take = Math.min(remaining, Math.max(0, level.count() - allocation.get(level.pool())));
            allocation.merge(level.pool(), take, Integer::sum);
            remaining -= take;
        }
        allocation.merge(Pool.TACTIC, remaining, Integer::sum);
        return allocation;
    }

    public static Map<Pool, Integer> idealKeepingFleet(Game game, Player seat, int tokens) {
        Map<Pool, Integer> allocation = new EnumMap<>(Pool.class);
        for (Pool pool : Pool.values()) allocation.put(pool, 0);
        allocation.put(Pool.FLEET, Math.min(tokens, seat.getFleetCC()));
        int remaining = tokens - allocation.get(Pool.FLEET);
        for (Level level : priorities(game, seat)) {
            if (level.pool() == Pool.FLEET) continue;
            int take = Math.min(remaining, Math.max(0, level.count() - allocation.get(level.pool())));
            allocation.merge(level.pool(), take, Integer::sum);
            remaining -= take;
        }
        allocation.merge(Pool.TACTIC, remaining, Integer::sum);
        return allocation;
    }

    public static Optional<String> midRoundSurplus(Game game, Player seat) {
        Map<Pool, Integer> ideal = idealKeepingFleet(game, seat, totalTokens(seat));
        for (Pool pool : List.of(Pool.STRATEGY, Pool.TACTIC)) {
            if (pool.count(seat) > ideal.get(pool)) return Optional.of(pool.shrink());
        }
        return Optional.empty();
    }

    public static String poolToRefill(Game game, Player seat) {
        Map<Pool, Integer> ideal = idealKeepingFleet(game, seat, totalTokens(seat) - netGainSoFar(game, seat));
        return List.of(Pool.TACTIC, Pool.STRATEGY).stream()
                .max(Comparator.comparingInt(pool -> ideal.get(pool) - pool.count(seat)))
                .orElse(Pool.TACTIC)
                .grow();
    }

    private static List<Level> priorities(Game game, Player seat) {
        int largest = largestStack(game, seat);
        int bonus = fleetBonus(seat);
        int cap = fleetCap(game, seat);
        if (ACTION_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
            TokenPlan plan = TokenPlan.of(game, seat);
            return List.of(
                    new Level(Pool.FLEET, Math.min(cap, largest) - bonus),
                    new Level(Pool.TACTIC, 1),
                    new Level(Pool.FLEET, Math.min(cap, BASE_FLEET) - bonus),
                    new Level(Pool.FLEET, Math.min(cap, fleetForAnOpenSlot(game, seat, largest, cap)) - bonus),
                    new Level(Pool.FLEET, Math.min(cap, objectiveFleet(game, seat, largest, cap)) - bonus),
                    new Level(Pool.FLEET, Math.min(cap, fractureFleet(game, seat, largest)) - bonus),
                    new Level(Pool.STRATEGY, plan.strategy()),
                    new Level(Pool.TACTIC, plan.tactic()),
                    new Level(Pool.FLEET, Math.min(cap, stackingFleet(game, seat, largest)) - bonus));
        }
        return List.of(
                new Level(Pool.FLEET, Math.min(cap, largest) - bonus),
                new Level(Pool.TACTIC, 1),
                new Level(Pool.FLEET, Math.min(cap, fleetForAnOpenSlot(game, seat, largest, cap)) - bonus),
                new Level(Pool.STRATEGY, strategizeHeld(game, seat) ? 1 : 0),
                new Level(Pool.TACTIC, TACTIC_FIRST),
                new Level(Pool.FLEET, Math.min(cap, buildFleet(game, seat, largest)) - bonus),
                new Level(Pool.FLEET, Math.min(cap, objectiveFleet(game, seat, largest, cap)) - bonus),
                new Level(Pool.FLEET, Math.min(cap, BASE_FLEET) - bonus),
                new Level(Pool.FLEET, Math.min(cap, fractureFleet(game, seat, largest)) - bonus),
                new Level(Pool.STRATEGY, strategyTarget(game, seat)),
                new Level(Pool.FLEET, Math.min(cap, stackingFleet(game, seat, largest)) - bonus),
                new Level(Pool.TACTIC, TACTIC_PLENTY),
                new Level(Pool.STRATEGY, STRATEGY_MAX));
    }

    private static boolean strategizeHeld(Game game, Player seat) {
        return ActionCardRules.holdsPlayableStrategize(game, seat);
    }

    private static int strategyTarget(Game game, Player seat) {
        boolean onARoute = seat.getSecretsUnscored().containsKey(TechPath.ADAPT_NEW_STRATEGIES)
                && TechPath.researchesNeeded(game, seat, TechPath.ADAPT_NEW_STRATEGIES)
                                .orElse(0)
                        > 0
                && TechPath.reachable(game, seat, TechPath.ADAPT_NEW_STRATEGIES);
        int target = onARoute ? STRATEGY_TARGET + 1 : STRATEGY_TARGET;
        return strategizeHeld(game, seat) ? target + 1 : target;
    }

    private static int fleetForAnOpenSlot(Game game, Player seat, int largest, int cap) {
        return holdsAnotherFreePublic(game, seat) ? 0 : objectiveFleet(game, seat, largest, cap);
    }

    private static boolean holdsAnotherFreePublic(Game game, Player seat) {
        return ObjectivePolicy.scorablePublics(game, seat).stream()
                .filter(id -> !ObjectiveCatalog.isSpend(id))
                .anyMatch(id -> !RAISE_A_FLEET.equals(id) && !COMMAND_AN_ARMADA.equals(id));
    }

    private static int objectiveFleet(Game game, Player seat, int largest, int cap) {
        int target = 0;
        if (wantsUnscored(game, seat, RAISE_A_FLEET) && cap >= RAISE_A_FLEET_SHIPS) target = RAISE_A_FLEET_SHIPS;
        if (wantsUnscored(game, seat, COMMAND_AN_ARMADA)) {
            int fieldable = nonFighterShipsOnBoard(game, seat) + SHIPS_BUILT_PER_ROUND;
            target = Math.max(target, Math.min(ARMADA_SHIPS, Math.max(RAISE_A_FLEET_SHIPS, fieldable)));
        }
        return Math.min(target, largest + SHIPS_BUILT_PER_ROUND);
    }

    private static int fractureFleet(Game game, Player seat, int largest) {
        boolean stackNearTheLimit = largest >= BASE_FLEET - 1;
        boolean ownRoute = FractureCampaign.routeShare(game, seat) >= 1;
        return stackNearTheLimit
                        && ownRoute
                        && FractureCampaign.dock(game, seat).isPresent()
                ? FractureCampaign.FLEET_SHIPS
                : 0;
    }

    private static int buildFleet(Game game, Player seat, int largest) {
        Tile home = seat.getHomeSystemTile();
        if (home == null) return 0;
        Integer resources = Helper.getPlayerResourcesAvailable(seat, game);
        boolean canBuildBig = Helper.getProductionValue(seat, game, home, false) >= BUILD_FLEET_MIN_CAPACITY
                && resources != null
                && resources >= BUILD_FLEET_MIN_RESOURCES;
        return canBuildBig ? Math.min(STACKING_FLEET_CAP, Math.max(BASE_FLEET, largest) + 1) : 0;
    }

    private static int stackingFleet(Game game, Player seat, int largest) {
        boolean shipsCouldJoin = nonFighterShipsOnBoard(game, seat) > largest;
        return shipsCouldJoin && largest >= BASE_FLEET ? Math.min(STACKING_FLEET_CAP, largest + 1) : 0;
    }

    private static int fleetCap(Game game, Player seat) {
        return ButtonHelper.isLawInPlay(game, FLEET_REGULATIONS)
                ? REGULATED_FLEET + fleetBonus(seat)
                : Integer.MAX_VALUE;
    }

    private static int fleetBonus(Player seat) {
        return FleetLimit.bonus(seat);
    }

    private static int totalTokens(Player seat) {
        return seat.getTacticalCC() + seat.getFleetCC() + seat.getStrategicCC();
    }

    private static int nonFighterShipsOnBoard(Game game, Player seat) {
        int ships = 0;
        for (Tile tile : game.getTileMap().values()) ships += BoardView.nonFighterShips(BoardView.space(tile), seat);
        return ships;
    }

    private static int largestStack(Game game, Player seat) {
        int largest = 0;
        for (Tile tile : game.getTileMap().values()) {
            largest = Math.max(largest, BoardView.nonFighterShips(BoardView.space(tile), seat));
        }
        return largest;
    }

    private static boolean wantsUnscored(Game game, Player seat, String objective) {
        return game.getRevealedPublicObjectives().containsKey(objective)
                && !game.getScoredPublicObjectives()
                        .getOrDefault(objective, List.of())
                        .contains(seat.getUserID());
    }
}
