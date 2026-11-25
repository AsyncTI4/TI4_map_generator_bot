package ti4.ai.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

final class MoveAllocation {

    private static final List<UnitType> CARGO_ORDER = List.of(UnitType.Mech, UnitType.Infantry, UnitType.Fighter);
    private static final double SCORE_EPSILON = 1e-9;
    private static final int NO_ROUTE = -1;

    record Stock(
            Tile origin,
            int calm,
            int rifting,
            Map<UnitType, Integer> ships,
            Map<UnitType, Integer> speeds,
            int localBoosts,
            Map<UnitType, Integer> cargo,
            boolean leashed,
            boolean flies,
            Map<UnitType, MovementGraph.Route> typeRoutes,
            Map<UnitType, Integer> liftedSpeeds) {

        Stock(
                Tile origin,
                int calm,
                int rifting,
                Map<UnitType, Integer> ships,
                Map<UnitType, Integer> speeds,
                int localBoosts,
                Map<UnitType, Integer> cargo,
                boolean leashed,
                boolean flies) {
            this(origin, calm, rifting, ships, speeds, localBoosts, cargo, leashed, flies, Map.of(), Map.of());
        }

        int count(UnitType type) {
            return ships.getOrDefault(type, 0);
        }

        int shortfall(UnitType type, int rolls) {
            return shortfallAt(type, rolls, speeds.getOrDefault(type, 0));
        }

        int shortfallAt(UnitType type, int rolls, int speed) {
            MovementGraph.Route own = typeRoutes.get(type);
            int distance = own == null ? (rolls == 0 ? calm : rifting) : (rolls == 0 ? own.calm() : own.rifting());
            return speed == 0 || distance == MovementGraph.NONE ? Integer.MAX_VALUE : Math.max(0, distance - speed);
        }

        int freeRolls(UnitType type) {
            if (shortfall(type, 0) == 0) return 0;
            if (shortfall(type, 1) == 0) return 1;
            return NO_ROUTE;
        }

        boolean hasMovingShips() {
            return BoardView.MOVING_SHIPS.stream().anyMatch(type -> count(type) > 0)
                    || (flies && count(UnitType.Fighter) > 0);
        }
    }

    private record Ride(UnitType type, int rolls) {}

    private record Pick(Map<Ride, Integer> pinned, int shared, boolean lifted, int rolls, boolean mendosa) {

        static final Pick NONE = new Pick(Map.of(), 0, false, 0, false);

        Pick(Map<Ride, Integer> pinned, int shared, boolean lifted, int rolls) {
            this(pinned, shared, lifted, rolls, false);
        }

        int pinnedShips() {
            return pinned.entrySet().stream()
                    .filter(entry -> entry.getKey().type() != UnitType.Fighter)
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        }

        int pinned(UnitType type) {
            return pinned.entrySet().stream()
                    .filter(entry -> entry.getKey().type() == type)
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        }
    }

    private record Option(UnitType type, int rolls, int boosts) {}

    private record Flight(int index, int rolls, int waiting) {}

    private final Game game;
    private final Player seat;
    private final ToDoubleFunction<UnitType> value;
    private final int supplySlots;
    private final int sharedBoosts;
    private final List<Stock> stocks;
    private final List<List<Pick>> options = new ArrayList<>();
    private Pick[] chosen;
    private Map<String, Map<UnitType, Integer>> best = Map.of();
    private double bestScore = Double.NEGATIVE_INFINITY;
    private int bestShared = Integer.MAX_VALUE;

    MoveAllocation(
            Game game,
            Player seat,
            ToDoubleFunction<UnitType> value,
            int supplySlots,
            int sharedBoosts,
            List<Stock> stocks) {
        this.game = game;
        this.seat = seat;
        this.value = value;
        this.supplySlots = supplySlots;
        this.sharedBoosts = sharedBoosts;
        this.stocks = stocks;
    }

    Map<String, Map<UnitType, Integer>> best() {
        if (stocks.isEmpty() || supplySlots <= 0) return Map.of();
        stocks.forEach(stock -> options.add(picksFor(stock)));
        chosen = new Pick[stocks.size()];
        explore(0, sharedBoosts, false);
        return best;
    }

    private void explore(int index, int sharedLeft, boolean liftUsed) {
        if (index == stocks.size()) {
            score(chosen.clone());
            return;
        }
        for (Pick pick : options.get(index)) {
            if (pick.shared() > sharedLeft || (pick.mendosa() && liftUsed)) continue;
            chosen[index] = pick;
            explore(index + 1, sharedLeft - pick.shared(), liftUsed || pick.mendosa());
        }
    }

    private List<Pick> picksFor(Stock stock) {
        List<Pick> picks = new ArrayList<>(stock.leashed() ? leashedPicks(stock) : boostedPicks(stock));
        picks.addAll(mendosaPicks(stock));
        return picks;
    }

    private List<Pick> mendosaPicks(Stock stock) {
        List<Pick> picks = new ArrayList<>();
        stock.liftedSpeeds().forEach((type, speed) -> {
            if (stock.count(type) <= 0) return;
            for (int rolls = 0; rolls <= 1; rolls++) {
                int lifted = stock.shortfallAt(type, rolls, speed);
                if (lifted == Integer.MAX_VALUE || lifted >= stock.shortfall(type, rolls)) continue;
                int need = Math.max(0, lifted - stock.localBoosts());
                if (need > sharedBoosts) continue;
                picks.add(new Pick(Map.of(new Ride(type, rolls), 1), need, stock.leashed(), rolls, true));
                return;
            }
        });
        return picks;
    }

    private List<Pick> leashedPicks(Stock stock) {
        if (!stock.hasMovingShips()) return List.of(Pick.NONE);
        Set<UnitType> setters = ShipSpeed.paceSetters(game, seat);
        List<Pick> picks = new ArrayList<>();
        int calmNeed = Integer.MAX_VALUE;
        for (int rolls = 0; rolls <= 1; rolls++) {
            int fewest = Integer.MAX_VALUE;
            UnitType pacer = null;
            for (UnitType type : setters) {
                if (stock.count(type) <= 0) continue;
                int shortfall = stock.shortfall(type, rolls);
                if (shortfall < fewest) {
                    fewest = shortfall;
                    pacer = type;
                }
            }
            if (pacer == null || fewest == Integer.MAX_VALUE) continue;
            int need = Math.max(0, fewest - stock.localBoosts());
            if (need > sharedBoosts || (rolls == 1 && need >= calmNeed)) continue;
            if (rolls == 0) calmNeed = need;
            picks.add(new Pick(Map.of(new Ride(pacer, rolls), 1), need, true, rolls));
        }
        if (calmNeed != 0) picks.addFirst(Pick.NONE);
        return picks;
    }

    private List<Pick> boostedPicks(Stock stock) {
        List<Option> boostable = new ArrayList<>();
        int most = stock.localBoosts() + sharedBoosts;
        for (UnitType type : BoardView.MOVING_SHIPS) {
            if (stock.count(type) <= 0) continue;
            int calm = stock.shortfall(type, 0);
            int rifting = stock.shortfall(type, 1);
            if (calm > 0 && calm <= most) boostable.add(new Option(type, 0, calm));
            if (rifting > 0 && rifting < calm && rifting <= most) boostable.add(new Option(type, 1, rifting));
        }
        List<Pick> picks = new ArrayList<>();
        Map<Ride, Integer> previous = null;
        for (int shared = 0; shared <= sharedBoosts; shared++) {
            Map<Ride, Integer> pinned = bestBoosted(stock, boostable, stock.localBoosts() + shared);
            if (pinned.equals(previous)) continue;
            int used = pinned.entrySet().stream()
                    .mapToInt(entry -> entry.getValue()
                            * stock.shortfall(
                                    entry.getKey().type(), entry.getKey().rolls()))
                    .sum();
            picks.add(new Pick(pinned, Math.max(0, used - stock.localBoosts()), false, 0));
            previous = pinned;
        }
        return picks;
    }

    private Map<Ride, Integer> bestBoosted(Stock stock, List<Option> boostable, int boosts) {
        Map<Ride, Integer> found = new LinkedHashMap<>();
        double[] foundWorth = {0};
        searchBoosted(
                stock,
                boostable,
                0,
                boosts,
                new LinkedHashMap<>(),
                EnumSet.noneOf(UnitType.class),
                0,
                found,
                foundWorth);
        return Map.copyOf(found);
    }

    private void searchBoosted(
            Stock stock,
            List<Option> boostable,
            int index,
            int boostsLeft,
            Map<Ride, Integer> current,
            Set<UnitType> used,
            double worth,
            Map<Ride, Integer> found,
            double[] foundWorth) {
        if (index == boostable.size()) {
            if (worth > foundWorth[0] + SCORE_EPSILON) {
                found.clear();
                found.putAll(current);
                foundWorth[0] = worth;
            }
            return;
        }
        Option option = boostable.get(index);
        searchBoosted(stock, boostable, index + 1, boostsLeft, current, used, worth, found, foundWorth);
        if (used.contains(option.type())) return;
        double each = soloWorth(stock, option.type(), option.rolls());
        if (each <= 0) return;
        int most = Math.min(stock.count(option.type()), boostsLeft / option.boosts());
        if (most <= 0) return;
        Ride ride = new Ride(option.type(), option.rolls());
        used.add(option.type());
        for (int taken = 1; taken <= most; taken++) {
            current.put(ride, taken);
            searchBoosted(
                    stock,
                    boostable,
                    index + 1,
                    boostsLeft - taken * option.boosts(),
                    current,
                    used,
                    worth + taken * each,
                    found,
                    foundWorth);
        }
        current.remove(ride);
        used.remove(option.type());
    }

    private double soloWorth(Stock stock, UnitType type, int rolls) {
        int capacity = BoardView.capacity(seat, type);
        double worth = value.applyAsDouble(type);
        for (UnitType cargo : CARGO_ORDER) {
            int carried = Math.min(capacity, stock.cargo().getOrDefault(cargo, 0));
            worth += carried * value.applyAsDouble(cargo);
            capacity -= carried;
        }
        return RiftRisk.worth(rolls) * worth;
    }

    private double stackWorth(Stock stock) {
        return BoardView.MOVING_SHIPS.stream()
                .mapToDouble(type -> stock.count(type) * value.applyAsDouble(type))
                .sum();
    }

    private void score(Pick[] picks) {
        trimPinsToSupply(picks);
        int pinnedShips = 0;
        int sharedUsed = 0;
        for (Pick pick : picks) {
            sharedUsed += pick.shared();
            pinnedShips += pick.pinnedShips();
        }
        Fill fill = new Fill();
        for (int index = 0; index < stocks.size(); index++) fill.pin(index, picks[index]);
        fill.top(supplySlots - pinnedShips);
        fill.fly(supplySlots - pinnedShips - fill.topped);
        boolean better = fill.score > bestScore + SCORE_EPSILON;
        boolean asGoodForLess = Math.abs(fill.score - bestScore) <= SCORE_EPSILON && sharedUsed < bestShared;
        if (better || asGoodForLess) {
            bestScore = fill.score;
            bestShared = sharedUsed;
            best = fill.result();
        }
    }

    private void trimPinsToSupply(Pick[] picks) {
        int pinned = 0;
        for (Pick pick : picks) pinned += pick.pinnedShips();
        while (pinned > supplySlots) {
            int cheapest = -1;
            for (int index = 0; index < picks.length; index++) {
                if (picks[index].pinnedShips() == 0) continue;
                if (cheapest < 0 || stackWorth(stocks.get(index)) < stackWorth(stocks.get(cheapest))) cheapest = index;
            }
            pinned -= picks[cheapest].pinnedShips();
            picks[cheapest] = Pick.NONE;
        }
    }

    private final class Fill {
        private final List<Map<UnitType, Integer>> taken = new ArrayList<>();
        private final List<Map<Ride, Integer>> free = new ArrayList<>();
        private final List<Map<UnitType, Integer>> cargoLeft = new ArrayList<>();
        private double score;
        private int topped;
        private int spareCapacity;

        void pin(int index, Pick pick) {
            Stock stock = stocks.get(index);
            Map<UnitType, Integer> cargo = new EnumMap<>(UnitType.class);
            cargo.putAll(stock.cargo());
            cargoLeft.add(cargo);
            taken.add(new EnumMap<>(UnitType.class));
            free.add(freeShips(stock, pick));
            pick.pinned().forEach((ride, count) -> {
                for (int i = 0; i < count; i++) take(index, ride);
            });
        }

        private Map<Ride, Integer> freeShips(Stock stock, Pick pick) {
            Map<Ride, Integer> open = new LinkedHashMap<>();
            for (UnitType type : BoardView.MOVING_SHIPS) {
                int left = stock.count(type) - pick.pinned(type);
                if (left <= 0) continue;
                int rolls = stock.leashed() ? (pick.lifted() ? pick.rolls() : NO_ROUTE) : stock.freeRolls(type);
                if (rolls != NO_ROUTE) open.put(new Ride(type, rolls), left);
            }
            return open;
        }

        private void take(int index, Ride ride) {
            taken.get(index).merge(ride.type(), 1, Integer::sum);
            if (ride.type() == UnitType.Fighter) {
                cargoLeft.get(index).merge(UnitType.Fighter, -1, Integer::sum);
                score += RiftRisk.worth(ride.rolls()) * value.applyAsDouble(UnitType.Fighter);
                return;
            }
            score += RiftRisk.worth(ride.rolls())
                    * (value.applyAsDouble(ride.type()) + carry(index, BoardView.capacity(seat, ride.type()), true));
        }

        private double carry(int index, int capacity, boolean load) {
            Map<UnitType, Integer> cargo = cargoLeft.get(index);
            double worth = 0;
            for (UnitType type : CARGO_ORDER) {
                int carried = Math.min(capacity, Math.max(0, cargo.getOrDefault(type, 0)));
                worth += carried * value.applyAsDouble(type);
                capacity -= carried;
                if (load && carried > 0) cargo.merge(type, -carried, Integer::sum);
            }
            if (load) spareCapacity += capacity;
            return worth;
        }

        void top(int slots) {
            for (int slot = 0; slot < slots; slot++) {
                int pickedIndex = -1;
                Ride picked = null;
                double pickedWorth = Double.NEGATIVE_INFINITY;
                for (int index = 0; index < free.size(); index++) {
                    for (Map.Entry<Ride, Integer> ship : free.get(index).entrySet()) {
                        if (ship.getValue() <= 0) continue;
                        Ride ride = ship.getKey();
                        double worth = RiftRisk.worth(ride.rolls())
                                * (value.applyAsDouble(ride.type())
                                        + carry(index, BoardView.capacity(seat, ride.type()), false));
                        if (ride.rolls() > 0 && worth <= 0) continue;
                        if (worth > pickedWorth + SCORE_EPSILON) {
                            pickedWorth = worth;
                            pickedIndex = index;
                            picked = ride;
                        }
                    }
                }
                if (pickedIndex < 0) return;
                free.get(pickedIndex).merge(picked, -1, Integer::sum);
                take(pickedIndex, picked);
                topped++;
            }
        }

        void fly(int slotsLeft) {
            int room = spareCapacity + Math.max(0, slotsLeft) * FighterWings.perFleetSlot(seat) - flown();
            List<Flight> wings = new ArrayList<>();
            for (int index = 0; index < stocks.size(); index++) {
                Stock stock = stocks.get(index);
                if (!stock.flies()) continue;
                int rolls = stock.freeRolls(UnitType.Fighter);
                if (rolls == NO_ROUTE || RiftRisk.worth(rolls) * value.applyAsDouble(UnitType.Fighter) <= 0) continue;
                int waiting = Math.max(0, cargoLeft.get(index).getOrDefault(UnitType.Fighter, 0));
                if (waiting > 0) wings.add(new Flight(index, rolls, waiting));
            }
            wings.sort(Comparator.comparingInt(Flight::rolls));
            for (Flight wing : wings) {
                for (int each = 0; each < wing.waiting() && room > 0; each++, room--) {
                    take(wing.index(), new Ride(UnitType.Fighter, wing.rolls()));
                }
            }
        }

        private int flown() {
            return taken.stream()
                    .mapToInt(ships -> ships.getOrDefault(UnitType.Fighter, 0))
                    .sum();
        }

        Map<String, Map<UnitType, Integer>> result() {
            Map<String, Map<UnitType, Integer>> result = new LinkedHashMap<>();
            for (int index = 0; index < stocks.size(); index++) {
                if (!taken.get(index).isEmpty())
                    result.put(stocks.get(index).origin().getPosition(), taken.get(index));
            }
            return result;
        }
    }
}
