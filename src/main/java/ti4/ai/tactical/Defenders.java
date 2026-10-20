package ti4.ai.tactical;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

record Defenders(Stack ships, Stack groundInSpace, Map<String, Stack> planets) {

    record Stack(Map<UnitType, Integer> units, Map<UnitType, Integer> damaged) {

        static final Stack EMPTY = new Stack(Map.of(), Map.of());

        static Stack of(Map<UnitType, Integer> units, Map<UnitType, Integer> damaged) {
            Map<UnitType, Integer> kept = new EnumMap<>(UnitType.class);
            Map<UnitType, Integer> hurt = new EnumMap<>(UnitType.class);
            units.forEach((type, count) -> {
                if (count > 0) kept.put(type, count);
            });
            damaged.forEach((type, count) -> {
                int possible = Math.min(count, kept.getOrDefault(type, 0));
                if (possible > 0) hurt.put(type, possible);
            });
            return new Stack(kept, hurt);
        }

        int count(UnitType type) {
            return units.getOrDefault(type, 0);
        }

        int total() {
            return units.values().stream().mapToInt(Integer::intValue).sum();
        }

        boolean isEmpty() {
            return units.isEmpty();
        }

        // TODO: departing ships are assumed to be the damaged ones first (the less defensive reading)
        Stack minus(UnitType type, int count) {
            int removed = Math.min(count, count(type));
            if (removed <= 0) return this;
            Map<UnitType, Integer> newUnits = new EnumMap<>(UnitType.class);
            newUnits.putAll(units);
            newUnits.merge(type, -removed, Integer::sum);
            Map<UnitType, Integer> newDamaged = new EnumMap<>(UnitType.class);
            newDamaged.putAll(damaged);
            int hurt = damaged.getOrDefault(type, 0);
            newDamaged.put(type, hurt - Math.min(hurt, removed));
            return of(newUnits, newDamaged);
        }

        Stack plus(UnitType type, int count, int damagedCount) {
            if (count <= 0) return this;
            Map<UnitType, Integer> newUnits = new EnumMap<>(UnitType.class);
            newUnits.putAll(units);
            newUnits.merge(type, count, Integer::sum);
            Map<UnitType, Integer> newDamaged = new EnumMap<>(UnitType.class);
            newDamaged.putAll(damaged);
            if (damagedCount > 0) newDamaged.merge(type, damagedCount, Integer::sum);
            return of(newUnits, newDamaged);
        }

        Stack keepingGround(int keep) {
            Stack kept = EMPTY;
            int room = keep;
            for (UnitType type : List.of(UnitType.Mech, UnitType.Infantry)) {
                int taken = Math.min(room, count(type));
                if (taken > 0) kept = kept.plus(type, taken, Math.min(taken, damaged.getOrDefault(type, 0)));
                room -= taken;
            }
            return kept;
        }

        String key() {
            Map<String, String> sorted = new TreeMap<>();
            units.forEach((type, count) -> sorted.put(type.name(), count + ":" + damaged.getOrDefault(type, 0)));
            StringBuilder key = new StringBuilder();
            sorted.forEach((type, counts) ->
                    key.append(type).append(':').append(counts).append(','));
            return key.toString();
        }
    }

    static Defenders of(Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        Map<UnitType, Integer> ships = BoardView.ships(space, seat);
        Stack shipStack = Stack.of(ships, BattleMath.damagedAmong(space, seat, ships));
        Stack spaceGround = groundOn(space, seat);
        Map<String, Stack> onPlanets = new LinkedHashMap<>();
        for (Planet planet : tile.getPlanetUnitHolders()) onPlanets.put(planet.getName(), groundOn(planet, seat));
        return new Defenders(shipStack, spaceGround, onPlanets);
    }

    private static Stack groundOn(UnitHolder holder, Player seat) {
        Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
        for (UnitType type : BoardView.GROUND_FORCES) ground.put(type, BoardView.count(holder, seat, type));
        return Stack.of(ground, BattleMath.damagedAmong(holder, seat, ground));
    }

    Stack on(String planet) {
        return planets.getOrDefault(planet, Stack.EMPTY);
    }

    int groundOn(String planet) {
        return on(planet).total();
    }

    Defenders minus(List<UnitMove> moves) {
        Stack newShips = ships;
        Stack newSpaceGround = groundInSpace;
        Map<String, Stack> newPlanets = new LinkedHashMap<>(planets);
        for (UnitMove move : moves) {
            if (!BoardView.SPACE.equals(move.holder())) {
                newPlanets.computeIfPresent(move.holder(), (name, stack) -> stack.minus(move.type(), move.count()));
            } else if (BoardView.GROUND_FORCES.contains(move.type())) {
                newSpaceGround = newSpaceGround.minus(move.type(), move.count());
            } else {
                newShips = newShips.minus(move.type(), move.count());
            }
        }
        return new Defenders(newShips, newSpaceGround, newPlanets);
    }

    Defenders arriving(List<UnitMove> moves) {
        Stack newShips = ships;
        Stack newSpaceGround = groundInSpace;
        for (UnitMove move : moves) {
            if (BoardView.GROUND_FORCES.contains(move.type())) {
                newSpaceGround = newSpaceGround.plus(move.type(), move.count(), 0);
            } else {
                newShips = newShips.plus(move.type(), move.count(), 0);
            }
        }
        return new Defenders(newShips, newSpaceGround, planets);
    }

    Defenders joinedInSpaceBy(Defenders other) {
        Stack newShips = ships;
        Stack newSpaceGround = groundInSpace;
        for (Map.Entry<UnitType, Integer> ship : other.ships().units().entrySet()) {
            newShips = newShips.plus(ship.getKey(), ship.getValue(), 0);
        }
        for (Map.Entry<UnitType, Integer> ground : other.groundInSpace().units().entrySet()) {
            newSpaceGround = newSpaceGround.plus(ground.getKey(), ground.getValue(), 0);
        }
        return new Defenders(newShips, newSpaceGround, planets);
    }

    boolean isEmpty() {
        return ships.isEmpty()
                && groundInSpace.isEmpty()
                && planets.values().stream().allMatch(Stack::isEmpty);
    }

    Defenders plusShips(UnitType type, int count, int damagedCount) {
        return new Defenders(ships.plus(type, count, damagedCount), groundInSpace, planets);
    }

    Defenders plusGround(String planet, UnitType type, int count) {
        Map<String, Stack> newPlanets = new LinkedHashMap<>(planets);
        newPlanets.put(planet, on(planet).plus(type, count, 0));
        return new Defenders(ships, groundInSpace, newPlanets);
    }

    Defenders withGround(String planet, int keep) {
        Map<String, Stack> newPlanets = new LinkedHashMap<>(planets);
        newPlanets.put(planet, on(planet).keepingGround(keep));
        return new Defenders(ships, groundInSpace, newPlanets);
    }

    double shipCost(Player seat) {
        double cost = 0;
        for (Map.Entry<UnitType, Integer> ship : ships.units().entrySet()) {
            cost += ship.getValue()
                    * BoardView.model(seat, ship.getKey())
                            .map(UnitModel::getCost)
                            .orElse(0f);
        }
        return cost;
    }

    String shipsKey() {
        return ships.key();
    }

    String groundKey(String planet) {
        return on(planet).key();
    }

    String key() {
        StringBuilder key = new StringBuilder(ships.key()).append('|').append(groundInSpace.key());
        new TreeMap<>(planets)
                .forEach((name, stack) ->
                        key.append('|').append(name).append('=').append(stack.key()));
        return key.toString();
    }
}
