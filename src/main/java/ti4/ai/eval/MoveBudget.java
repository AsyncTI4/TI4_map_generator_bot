package ti4.ai.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.TickMemo;
import ti4.ai.legendary.LegendaryPlanets;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class MoveBudget {

    private static final String GRAVITY_DRIVE = "gd";
    private static final String IMPERATOR = "winnubt";
    private static final String GRAVLEASH_MANEUVERS = "letnevbt";
    private static final String TEMPESTA_USED = "tempestaUsed";
    private static final String BEST_STAGING_MEMO = "move.bestStaging";

    public static int oneShip(Game game, Player seat, @Nullable Tile target) {
        return oneShip(game, seat, target, false);
    }

    public static int oneShip(Game game, Player seat, @Nullable Tile target, boolean assumeGravityDrive) {
        return gravityDrive(seat, assumeGravityDrive)
                + (tempestaThisAction(game, seat) ? 1 : 0)
                + imperator(seat, target);
    }

    public static int everyActionOneShip(Player seat, @Nullable Tile target) {
        return gravityDrive(seat, false) + imperator(seat, target);
    }

    public static int oneShipCeiling(Game game, Player seat) {
        int imperator = seat.hasUnlockedBreakthrough(IMPERATOR) ? 1 : 0;
        return gravityDrive(seat, false) + (tempestaThisAction(game, seat) ? 1 : 0) + imperator;
    }

    public static int oneShipInLaterRound(Player seat, @Nullable Tile target) {
        return oneShipInLaterRound(seat, target, false);
    }

    public static int oneShipInLaterRound(Player seat, @Nullable Tile target, boolean assumeGravityDrive) {
        int tempesta = seat.hasPlanet(LegendaryPlanets.TEMPESTA) ? 1 : 0;
        return gravityDrive(seat, assumeGravityDrive) + tempesta + imperator(seat, target);
    }

    public static int laterRoundBudget(
            Game game, Player seat, UnitType type, Tile origin, @Nullable Tile target, int legs) {
        int departing = laterRoundDeparture(game, seat, type, origin, legs);
        return departing == 0 ? 0 : departing + laterRoundArrival(game, seat, target);
    }

    public static int laterRoundCeiling(Game game, Player seat, UnitType type, Tile origin, int legs) {
        int departing = laterRoundDeparture(game, seat, type, origin, legs);
        return departing == 0 ? 0 : departing + laterRoundArrivalCeiling(game, seat);
    }

    public static int laterRoundArrival(Game game, Player seat, @Nullable Tile target) {
        return ShipSpeed.arrivalBonus(game, seat, target) + oneShipInLaterRound(seat, target);
    }

    private static int laterRoundDeparture(Game game, Player seat, UnitType type, Tile origin, int legs) {
        int departing = legBudget(game, seat, type, origin, null, false);
        if (departing == 0 || legs <= 0) return 0;
        int laterLeg = oneShipInLaterRound(seat, null) + ShipSpeed.printed(game, seat, type);
        return departing + (legs - 1) * laterLeg;
    }

    private static int laterRoundArrivalCeiling(Game game, Player seat) {
        int imperator = seat.hasUnlockedBreakthrough(IMPERATOR) ? 1 : 0;
        return ShipSpeed.arrivalBonusCeiling(game, seat) + oneShipInLaterRound(seat, null) + imperator;
    }

    public static boolean tempestaThisAction(Game game, Player seat) {
        if (LegendaryPlanets.abilityReady(seat, LegendaryPlanets.TEMPESTA)) return true;
        return seat.getFaction().equals(game.getStoredValue(TEMPESTA_USED))
                && TurnState.isActive(game, seat)
                && TurnState.activatedThisTurn(game, seat);
    }

    public static boolean hasGravleash(Player seat) {
        return seat.hasUnlockedBreakthrough(GRAVLEASH_MANEUVERS);
    }

    private static int gravityDrive(Player seat, boolean assumed) {
        return assumed || seat.hasTech(GRAVITY_DRIVE) ? 1 : 0;
    }

    private static int imperator(Player seat, @Nullable Tile target) {
        return target != null && seat.hasUnlockedBreakthrough(IMPERATOR) && target.hasLegendary() ? 1 : 0;
    }

    private static Set<UnitType> movers(Game game, Player seat) {
        return ShipSpeed.paceSetters(game, seat);
    }

    public static int searchBudget(Game game, Player seat, Tile origin) {
        return searchBudget(
                game,
                seat,
                origin,
                BoardView.ships(BoardView.space(origin), seat).keySet());
    }

    private static int searchBudget(Game game, Player seat, Tile origin, Collection<UnitType> types) {
        Set<UnitType> movers = movers(game, seat);
        int fastest = types.stream()
                .filter(movers::contains)
                .mapToInt(type -> ShipSpeed.base(game, seat, type, origin))
                .max()
                .orElse(0);
        if (fastest == 0) return 0;
        return fastest + ShipSpeed.fleetBonusCeiling(game, seat, origin) + oneShipCeiling(game, seat);
    }

    @Nullable
    private static Integer distance(Game game, Player seat, Tile origin, Tile target, int budget) {
        if (budget <= 0) return null;
        return MovementGraph.distance(game, seat, origin.getPosition(), target.getPosition(), budget, Set.of());
    }

    @Nullable
    private static MovementGraph.Route route(
            Game game, Player seat, Tile origin, Tile target, int budget, Set<String> screened) {
        return MovementGraph.route(game, seat, origin.getPosition(), target.getPosition(), budget, screened);
    }

    @Nullable
    private static MovementGraph.Route groupRoute(
            Game game, Player seat, Tile origin, Tile target, List<Departure> group) {
        Set<UnitType> types = EnumSet.noneOf(UnitType.class);
        types.addAll(BoardView.ships(BoardView.space(origin), seat).keySet());
        group.forEach(move -> types.add(move.type()));
        return route(game, seat, origin, target, searchBudget(game, seat, origin, types), Set.of());
    }

    @Nullable
    private static MovementGraph.Route memoriaRoute(
            Game game, Player seat, Tile origin, Tile target, List<Departure> group) {
        boolean flagship = group.stream().anyMatch(move -> move.type() == UnitType.Flagship && move.count() > 0);
        if (!flagship || !EnemyShortcuts.hasMemoria(game, seat)) return null;
        int budget = ShipSpeed.of(game, seat, UnitType.Flagship, origin, target) + oneShip(game, seat, target);
        return EnemyShortcuts.memoria(game, seat, origin, target, budget, Set.of());
    }

    public static int boostsNeeded(Game game, Player seat, Tile target, List<Departure> moves) {
        int needed = 0;
        for (Map.Entry<String, List<Departure>> group :
                departingGroups(game, seat, target, moves).entrySet()) {
            Tile origin = game.getTileByPosition(group.getKey());
            if (origin == null) continue;
            int shortfall = groupShortfall(game, seat, origin, target, group.getValue());
            if (shortfall == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            needed += shortfall;
        }
        return needed;
    }

    public static boolean feasible(Game game, Player seat, Tile target, List<Departure> moves) {
        return boostsNeeded(game, seat, target, moves) <= oneShip(game, seat, target);
    }

    private record RiftOption(int rolls, int boosts) {}

    private record RiftItem(List<Departure> ships, List<RiftOption> options) {

        boolean movesCalmlyUnaided() {
            return options.stream().anyMatch(option -> option.rolls() == 0 && option.boosts() == 0);
        }

        @Nullable
        RiftOption option(int rolls) {
            return options.stream()
                    .filter(option -> option.rolls() == rolls)
                    .findFirst()
                    .orElse(null);
        }
    }

    public static List<Departure> riftExits(Game game, Player seat, Tile target, List<Departure> moves) {
        List<RiftItem> items = riftItems(game, seat, target, moves);
        if (items.stream().allMatch(item -> item.options().stream().allMatch(option -> option.rolls() == 0))) {
            return List.of();
        }
        Map<RiftItem, Integer> kinds = new LinkedHashMap<>();
        items.forEach(item -> kinds.merge(item, 1, Integer::sum));
        RiftSearch search = new RiftSearch(seat, target, moves, new ArrayList<>(kinds.entrySet()));
        search.from(0, oneShip(game, seat, target), new ArrayList<>(), 0);
        if (search.best == null) {
            return aggregate(
                    items.stream().filter(item -> !item.movesCalmlyUnaided()).flatMap(item -> item.ships().stream()));
        }
        return search.best;
    }

    private static final class RiftSearch {

        private final Player seat;
        private final Tile target;
        private final List<Departure> moves;
        private final List<Map.Entry<RiftItem, Integer>> kinds;

        @Nullable
        private List<Departure> best;

        private double bestLoss = Double.POSITIVE_INFINITY;
        private int bestBoosts = Integer.MAX_VALUE;

        private RiftSearch(Player seat, Tile target, List<Departure> moves, List<Map.Entry<RiftItem, Integer>> kinds) {
            this.seat = seat;
            this.target = target;
            this.moves = moves;
            this.kinds = kinds;
        }

        private void from(int index, int boostsLeft, List<Departure> rolling, int boostsUsed) {
            if (index == kinds.size()) {
                settle(rolling, boostsUsed);
                return;
            }
            RiftItem item = kinds.get(index).getKey();
            int count = kinds.get(index).getValue();
            @Nullable RiftOption calm = item.option(0);
            @Nullable RiftOption roll = item.option(1);
            boolean calmDominates = calm != null && (roll == null || calm.boosts() <= roll.boosts());
            int fewestCalm = calmDominates ? count : 0;
            for (int calmCount = count; calmCount >= fewestCalm; calmCount--) {
                if (calmCount > 0 && calm == null) continue;
                if (calmCount < count && roll == null) continue;
                int boosts = calmCount * (calm == null ? 0 : calm.boosts())
                        + (count - calmCount) * (roll == null ? 0 : roll.boosts());
                if (boosts > boostsLeft) continue;
                int before = rolling.size();
                for (int each = calmCount; each < count; each++) rolling.addAll(item.ships());
                from(index + 1, boostsLeft - boosts, rolling, boostsUsed + boosts);
                rolling.subList(before, rolling.size()).clear();
            }
        }

        private void settle(List<Departure> rolling, int boostsUsed) {
            List<Departure> exits = aggregate(rolling.stream());
            double loss = RiftRisk.expectedLoss(seat, target, moves, exits, type -> unitCost(seat, type));
            boolean lower = loss < bestLoss - 1e-9;
            boolean asLowWithFewerBoosts = Math.abs(loss - bestLoss) <= 1e-9 && boostsUsed < bestBoosts;
            if (!lower && !asLowWithFewerBoosts) return;
            best = exits;
            bestLoss = loss;
            bestBoosts = boostsUsed;
        }
    }

    private static List<Departure> aggregate(Stream<Departure> ships) {
        Map<String, Map<UnitType, Integer>> counts = new LinkedHashMap<>();
        ships.forEach(move -> counts.computeIfAbsent(move.origin(), ignored -> new EnumMap<>(UnitType.class))
                .merge(move.type(), move.count(), Integer::sum));
        List<Departure> found = new ArrayList<>();
        counts.forEach(
                (origin, types) -> types.forEach((type, count) -> found.add(new Departure(origin, type, count))));
        return found;
    }

    private static List<RiftItem> riftItems(Game game, Player seat, Tile target, List<Departure> moves) {
        List<RiftItem> items = new ArrayList<>();
        departingGroups(game, seat, target, moves).forEach((position, group) -> {
            Tile origin = game.getTileByPosition(position);
            if (origin == null) return;
            List<Departure> ships = nonFighterShips(group);
            Wing wing = wing(seat, group);
            if (ships.isEmpty() && wing.flying() == 0) return;
            MovementGraph.Route route = groupRoute(game, seat, origin, target, group);
            int flyingAlone = wing.flying();
            if (hasGravleash(seat)) {
                Pace pace = leashPace(game, seat, origin, target, ships, wing);
                List<Departure> leashed = new ArrayList<>(ships);
                if (pace.fighter()) {
                    leashed.add(new Departure(position, UnitType.Fighter, 1));
                    flyingAlone = Math.max(0, flyingAlone - 1);
                }
                items.add(new RiftItem(leashed, riftOptions(route, pace.speed())));
            } else {
                for (Departure move : ships) {
                    addEach(items, game, seat, origin, target, route, move.type(), move.count());
                }
            }
            addEach(items, game, seat, origin, target, route, UnitType.Fighter, flyingAlone);
        });
        return items;
    }

    private static void addEach(
            List<RiftItem> items,
            Game game,
            Player seat,
            Tile origin,
            Tile target,
            @Nullable MovementGraph.Route route,
            UnitType type,
            int count) {
        if (count <= 0) return;
        List<RiftOption> options = riftOptions(route, ShipSpeed.of(game, seat, type, origin, target));
        for (int each = 0; each < count; each++) {
            items.add(new RiftItem(List.of(new Departure(origin.getPosition(), type, 1)), options));
        }
    }

    record Wing(int carried, int flying) {}

    private record Pace(int speed, boolean fighter) {}

    static Wing wing(Player seat, Collection<Departure> group) {
        int fighters = 0;
        int ground = 0;
        int capacity = 0;
        for (Departure move : group) {
            if (move.count() <= 0) continue;
            if (move.type() == UnitType.Fighter) {
                fighters += move.count();
            } else if (BoardView.GROUND_FORCES.contains(move.type())) {
                ground += move.count();
            } else if (BoardView.MOVING_SHIPS.contains(move.type())) {
                capacity += move.count() * BoardView.capacity(seat, move.type());
            }
        }
        int carried = Math.min(fighters, Math.max(0, capacity - ground));
        return new Wing(carried, fighters - carried);
    }

    private static List<Departure> nonFighterShips(List<Departure> group) {
        return group.stream()
                .filter(move -> move.count() > 0 && BoardView.MOVING_SHIPS.contains(move.type()))
                .toList();
    }

    private static Pace leashPace(Game game, Player seat, Tile origin, Tile target, List<Departure> ships, Wing wing) {
        int shipPace = groupSpeed(game, seat, origin, target, ships, 0);
        boolean fighters = wing.carried() + wing.flying() > 0 && FighterWings.flies(game, seat);
        int fighterPace = fighters ? ShipSpeed.of(game, seat, UnitType.Fighter, origin, target) : 0;
        boolean fighterLeads =
                fighterPace > shipPace || (fighterPace > 0 && fighterPace == shipPace && wing.flying() > 0);
        return fighterLeads ? new Pace(fighterPace, true) : new Pace(shipPace, false);
    }

    private static List<RiftOption> riftOptions(@Nullable MovementGraph.Route route, int speed) {
        if (route == null || speed == 0) return List.of();
        List<RiftOption> options = new ArrayList<>();
        if (route.calmlyReaches()) options.add(new RiftOption(0, Math.max(0, route.calm() - speed)));
        if (route.rifting() < route.calm()) options.add(new RiftOption(1, Math.max(0, route.rifting() - speed)));
        return options;
    }

    private static double unitCost(Player seat, UnitType type) {
        return BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
    }

    private static Map<String, List<Departure>> departingGroups(
            Game game, Player seat, Tile target, List<Departure> moves) {
        return moves.stream()
                .filter(move -> move.count() > 0)
                .filter(move -> !move.origin().equals(target.getPosition()))
                .collect(Collectors.groupingBy(Departure::origin, LinkedHashMap::new, Collectors.toList()));
    }

    private static int groupShortfall(Game game, Player seat, Tile origin, Tile target, List<Departure> group) {
        List<Departure> ships = nonFighterShips(group);
        Wing wing = wing(seat, group);
        if (ships.isEmpty() && wing.flying() == 0) return 0;
        if (wing.flying() > 0 && !FighterWings.flies(game, seat)) return Integer.MAX_VALUE;
        MovementGraph.Route route = groupRoute(game, seat, origin, target, group);
        int flyingAlone = wing.flying();
        int needed = 0;
        if (hasGravleash(seat)) {
            if (route == null) return Integer.MAX_VALUE;
            Pace pace = leashPace(game, seat, origin, target, ships, wing);
            if (pace.speed() == 0) return Integer.MAX_VALUE;
            needed = Math.max(0, route.rifting() - pace.speed());
            if (pace.fighter()) flyingAlone = Math.max(0, flyingAlone - 1);
        } else {
            @Nullable MovementGraph.Route memoria = memoriaRoute(game, seat, origin, target, group);
            for (Departure move : ships) {
                int speed = ShipSpeed.of(game, seat, move.type(), origin, target);
                MovementGraph.Route own =
                        move.type() == UnitType.Flagship ? EnemyShortcuts.shorter(route, memoria) : route;
                if (speed == 0 || own == null || own.rifting() == MovementGraph.NONE) return Integer.MAX_VALUE;
                needed += move.count() * Math.max(0, own.rifting() - speed);
            }
        }
        if (flyingAlone == 0) return needed;
        if (route == null) return Integer.MAX_VALUE;
        int distance = route.rifting();
        int fighterSpeed = ShipSpeed.of(game, seat, UnitType.Fighter, origin, target);
        if (fighterSpeed == 0) return Integer.MAX_VALUE;
        return needed + flyingAlone * Math.max(0, distance - fighterSpeed);
    }

    static int groupSpeed(
            Game game,
            Player seat,
            Tile origin,
            @Nullable Tile target,
            Collection<Departure> group,
            int boostsOnPacer) {
        Set<UnitType> setters = ShipSpeed.paceSetters(game, seat);
        int pace = group.stream()
                .filter(move -> move.count() > 0 && setters.contains(move.type()))
                .filter(move -> move.origin().equals(origin.getPosition()))
                .mapToInt(move -> ShipSpeed.of(game, seat, move.type(), origin, target))
                .max()
                .orElse(0);
        return pace == 0 ? 0 : pace + boostsOnPacer;
    }

    public static boolean canJoin(Game game, Player seat, Tile origin, Tile target, UnitType type) {
        if (!ShipSpeed.paceSetters(game, seat).contains(type)) return false;
        if (origin.getPosition().equals(target.getPosition())) return true;
        int speed = hasGravleash(seat)
                ? leashedSpeed(game, seat, origin, target, type)
                : ShipSpeed.of(game, seat, type, origin, target);
        if (speed == 0) return false;
        return distance(game, seat, origin, target, speed + oneShip(game, seat, target)) != null;
    }

    private static int leashedSpeed(Game game, Player seat, Tile origin, Tile target, UnitType type) {
        Set<UnitType> present = EnumSet.of(type);
        present.addAll(BoardView.ships(BoardView.space(origin), seat).keySet());
        Set<UnitType> setters = ShipSpeed.paceSetters(game, seat);
        return present.stream()
                .filter(setters::contains)
                .mapToInt(each -> ShipSpeed.of(game, seat, each, origin, target))
                .max()
                .orElse(0);
    }

    public static int legBudget(Game game, Player seat, UnitType type, Tile from, @Nullable Tile to, boolean finalLeg) {
        int base = ShipSpeed.base(game, seat, type, from);
        if (base == 0) return 0;
        return base + ShipSpeed.originBonus(game, seat, from) + (finalLeg ? ShipSpeed.targetBonus(game, seat, to) : 0);
    }

    public static Map<String, Map<UnitType, Integer>> allocate(
            Game game,
            Player seat,
            Tile target,
            Map<String, Map<UnitType, Integer>> available,
            ToDoubleFunction<UnitType> value,
            int supplySlots) {
        int shared = oneShip(game, seat, target);
        List<MoveAllocation.Stock> stocks = new ArrayList<>();
        available.forEach((position, ships) -> {
            Tile origin = game.getTileByPosition(position);
            if (origin == null || position.equals(target.getPosition())) return;
            Map<UnitType, Integer> speeds = new EnumMap<>(UnitType.class);
            ships.keySet().forEach(type -> speeds.put(type, ShipSpeed.of(game, seat, type, origin, target)));
            stock(game, seat, origin, target, ships, speeds, 0, shared, Set.of(), 0)
                    .ifPresent(stocks::add);
        });
        return new MoveAllocation(game, seat, value, supplySlots, shared, stocks).best();
    }

    public static Map<String, Map<UnitType, Integer>> shipsWithin(
            Game game,
            Player seat,
            Tile target,
            Journey journey,
            Predicate<Tile> origins,
            ToDoubleFunction<UnitType> value,
            int supplySlots) {
        return shipsWithin(game, seat, target, journey, origins, value, supplySlots, Set.of());
    }

    public static Map<String, Map<UnitType, Integer>> shipsWithin(
            Game game,
            Player seat,
            Tile target,
            Journey journey,
            Predicate<Tile> origins,
            ToDoubleFunction<UnitType> value,
            int supplySlots,
            Set<String> screened) {
        boolean finalLegNow = journey.legs() == 1 && journey.firstLegThisRound();
        if (!finalLegNow) {
            return MoveEnablers.laterActivation(
                    () -> gatherWithin(game, seat, target, journey, origins, value, supplySlots, screened, false));
        }
        return gatherWithin(game, seat, target, journey, origins, value, supplySlots, screened, true);
    }

    private static Map<String, Map<UnitType, Integer>> gatherWithin(
            Game game,
            Player seat,
            Tile target,
            Journey journey,
            Predicate<Tile> origins,
            ToDoubleFunction<UnitType> value,
            int supplySlots,
            Set<String> screened,
            boolean finalLegNow) {
        int shared = finalLegNow ? oneShip(game, seat, target) : oneShipInLaterRound(seat, target);
        List<MoveAllocation.Stock> stocks = new ArrayList<>();
        int lift = finalLegNow ? EnemyShortcuts.mendosaLift(game, seat) : 0;
        for (Tile origin : game.getTileMap().values()) {
            if (origin.getPosition().equals(target.getPosition()) || !origins.test(origin)) continue;
            Map<UnitType, Integer> ships = BoardView.ships(BoardView.space(origin), seat);
            if (ships.isEmpty()) continue;
            boolean movesNow = journey.firstLegThisRound() && firstLegCanStart(game, seat, origin, target, journey);
            int legs = journey.legsFrom(movesNow);
            if (legs <= 0) continue;
            Map<UnitType, Integer> speeds = new EnumMap<>(UnitType.class);
            ships.keySet()
                    .forEach(type ->
                            speeds.put(type, journeySpeed(game, seat, type, origin, target, journey, legs, movesNow)));
            int localBoosts = gravityDrive(seat, false) * (legs - 1);
            stock(game, seat, origin, target, ships, speeds, localBoosts, shared, screened, lift)
                    .ifPresent(stocks::add);
        }
        return new MoveAllocation(game, seat, value, supplySlots, shared, stocks).best();
    }

    private static boolean firstLegCanStart(Game game, Player seat, Tile origin, Tile target, Journey journey) {
        if (origin.hasPlayerCC(seat)) return false;
        if (journey.legs() == 1) return TurnState.canActivate(game, seat, target);
        return TurnState.canActivateAnotherSystem(game, seat)
                || TurnState.inOwnActivation(game, seat, journey.staging());
    }

    private static int journeySpeed(
            Game game,
            Player seat,
            UnitType type,
            Tile origin,
            Tile target,
            Journey journey,
            int legs,
            boolean movesNow) {
        int printed = ShipSpeed.printed(game, seat, type);
        if (printed == 0) return 0;
        int arrival = ShipSpeed.arrivalBonus(game, seat, target);
        int departure = ShipSpeed.base(game, seat, type, origin) + ShipSpeed.originBonus(game, seat, origin);
        if (legs == 1) return departure + arrival + (movesNow && ShipSpeed.flankSpeed(game, seat, target) ? 1 : 0);
        int first = departure
                + ShipSpeed.arrivalBonus(game, seat, journey.staging())
                + (movesNow && ShipSpeed.flankSpeed(game, seat, journey.staging()) ? 1 : 0);
        int stagingBonus = stagingBonus(game, seat, origin, journey, first);
        @Nullable Tile staging = journey.staging();
        int finalBase = staging == null ? printed : ShipSpeed.base(game, seat, type, staging);
        int middle = (legs - 2) * (printed + stagingBonus);
        return first + middle + finalBase + stagingBonus + arrival;
    }

    private static int stagingBonus(Game game, Player seat, Tile origin, Journey journey, int firstLeg) {
        if (journey.staging() != null) return ShipSpeed.originBonus(game, seat, journey.staging());
        if (!journey.bestStaging() || firstLeg <= 0) return 0;
        Map<String, Integer> memo = TickMemo.mapFor(game, BEST_STAGING_MEMO);
        return memo.computeIfAbsent(
                seat.getFaction() + "#" + origin.getPosition() + "#" + firstLeg + "#" + MovementGraph.assumesAntimass()
                        + "#" + MoveEnablers.key(game, seat),
                key -> bestStagingBonus(game, seat, origin, firstLeg));
    }

    private static int bestStagingBonus(Game game, Player seat, Tile origin, int firstLeg) {
        return MovementGraph.distances(game, seat, origin.getPosition(), firstLeg).keySet().stream()
                .filter(position -> !position.equals(origin.getPosition()))
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .mapToInt(tile -> ShipSpeed.originBonus(game, seat, tile))
                .max()
                .orElse(0);
    }

    private static Optional<MoveAllocation.Stock> stock(
            Game game,
            Player seat,
            Tile origin,
            Tile target,
            Map<UnitType, Integer> ships,
            Map<UnitType, Integer> speeds,
            int localBoosts,
            int shared,
            Set<String> screened,
            int lift) {
        Set<UnitType> movers = movers(game, seat);
        int fastest = speeds.entrySet().stream()
                .filter(entry -> movers.contains(entry.getKey()) && ships.getOrDefault(entry.getKey(), 0) > 0)
                .mapToInt(Map.Entry::getValue)
                .max()
                .orElse(0);
        if (fastest == 0) return Optional.empty();
        Map<UnitType, Integer> lifted = liftedSpeeds(game, seat, origin, ships, speeds, lift);
        int searched = Math.max(
                fastest,
                lifted.values().stream().mapToInt(Integer::intValue).max().orElse(0));
        MovementGraph.Route route = route(game, seat, origin, target, searched + localBoosts + shared, screened);
        @Nullable
        MovementGraph.Route memoria = ships.getOrDefault(UnitType.Flagship, 0) > 0
                ? EnemyShortcuts.memoria(
                        game,
                        seat,
                        origin,
                        target,
                        Math.max(speeds.getOrDefault(UnitType.Flagship, 0), lifted.getOrDefault(UnitType.Flagship, 0))
                                + localBoosts
                                + shared,
                        screened)
                : null;
        if (route == null && memoria == null) return Optional.empty();
        MovementGraph.Route base =
                route == null ? new MovementGraph.Route(MovementGraph.NONE, MovementGraph.NONE) : route;
        Map<UnitType, MovementGraph.Route> typeRoutes =
                memoria == null ? Map.of() : Map.of(UnitType.Flagship, EnemyShortcuts.shorter(base, memoria));
        return Optional.of(new MoveAllocation.Stock(
                origin,
                base.calm(),
                base.rifting(),
                ships,
                speeds,
                localBoosts,
                cargoAt(seat, origin, ships),
                hasGravleash(seat),
                FighterWings.flies(game, seat),
                typeRoutes,
                lifted));
    }

    private static Map<UnitType, Integer> liftedSpeeds(
            Game game,
            Player seat,
            Tile origin,
            Map<UnitType, Integer> ships,
            Map<UnitType, Integer> speeds,
            int lift) {
        if (lift <= 0) return Map.of();
        Map<UnitType, Integer> lifted = new EnumMap<>(UnitType.class);
        for (UnitType type : movers(game, seat)) {
            if (ships.getOrDefault(type, 0) <= 0) continue;
            int base = ShipSpeed.base(game, seat, type, origin);
            int speed = speeds.getOrDefault(type, 0);
            if (base == 0 || speed == 0) continue;
            int raised = lift + speed - base;
            if (raised > speed) lifted.put(type, raised);
        }
        return lifted;
    }

    private static Map<UnitType, Integer> cargoAt(Player seat, Tile origin, Map<UnitType, Integer> ships) {
        Map<UnitType, Integer> cargo = new EnumMap<>(UnitType.class);
        for (UnitType type : BoardView.GROUND_FORCES) {
            int count = BoardView.count(BoardView.space(origin), seat, type);
            for (Planet planet : origin.getPlanetUnitHolders()) {
                if (seat.getPlanets().contains(planet.getName())) count += BoardView.count(planet, seat, type);
            }
            if (count > 0) cargo.put(type, count);
        }
        int fighters = ships.getOrDefault(UnitType.Fighter, 0);
        if (fighters > 0) cargo.put(UnitType.Fighter, fighters);
        return cargo;
    }
}
