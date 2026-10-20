package ti4.ai.scoring;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;

public final class Footprint {

    public record Move(String origin, String holder, UnitType type, int count) {
        public Move(String origin, UnitType type, int count) {
            this(origin, BoardView.SPACE, type, count);
        }
    }

    private record Ground(Map<String, Integer> forces, Map<String, Integer> mechs, Set<String> docks) {}

    private final Map<String, Integer> units;
    private final Map<String, Integer> ships;
    private final Map<String, Integer> nonFighterShips;
    private final Map<String, Integer> heavyShips;
    private final Set<String> planets;
    private final int dreadnoughts;
    private final Ground ground;

    private Footprint(
            Map<String, Integer> units,
            Map<String, Integer> ships,
            Map<String, Integer> nonFighterShips,
            Map<String, Integer> heavyShips,
            Set<String> planets,
            int dreadnoughts,
            Ground ground) {
        this.units = units;
        this.ships = ships;
        this.nonFighterShips = nonFighterShips;
        this.heavyShips = heavyShips;
        this.planets = planets;
        this.dreadnoughts = dreadnoughts;
        this.ground = ground;
    }

    private static boolean isHeavy(UnitType type) {
        return type == UnitType.Flagship || type == UnitType.Warsun;
    }

    public static Footprint of(Game game, Player seat) {
        Map<String, Integer> units = new HashMap<>();
        Map<String, Integer> ships = new HashMap<>();
        Map<String, Integer> nonFighterShips = new HashMap<>();
        Map<String, Integer> heavyShips = new HashMap<>();
        int dreadnoughts = 0;
        for (Tile tile : game.getTileMap().values()) {
            int count = 0;
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey key : holder.getUnitKeysForPlayer(seat)) count += holder.getUnitCount(key);
            }
            if (count == 0) continue;
            String position = tile.getPosition();
            units.put(position, count);
            Map<UnitType, Integer> shipsHere = BoardView.ships(BoardView.space(tile), seat);
            int allShips =
                    shipsHere.values().stream().mapToInt(Integer::intValue).sum();
            if (allShips > 0) ships.put(position, allShips);
            int capital = allShips - shipsHere.getOrDefault(UnitType.Fighter, 0);
            if (capital > 0) nonFighterShips.put(position, capital);
            int heavy = shipsHere.getOrDefault(UnitType.Flagship, 0) + shipsHere.getOrDefault(UnitType.Warsun, 0);
            if (heavy > 0) heavyShips.put(position, heavy);
            dreadnoughts += shipsHere.getOrDefault(UnitType.Dreadnought, 0);
        }
        return new Footprint(
                units,
                ships,
                nonFighterShips,
                heavyShips,
                new HashSet<>(seat.getPlanets()),
                dreadnoughts,
                ground(game, seat));
    }

    private static Ground ground(Game game, Player seat) {
        Map<String, Integer> forces = new HashMap<>();
        Map<String, Integer> mechs = new HashMap<>();
        Set<String> docks = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                int here = BoardView.groundForces(planet, seat);
                if (here > 0) forces.put(planet.getName(), here);
                int mechsHere = BoardView.count(planet, seat, UnitType.Mech);
                if (mechsHere > 0) mechs.put(planet.getName(), mechsHere);
                if (BoardView.count(planet, seat, UnitType.Spacedock) > 0) docks.add(planet.getName());
            }
        }
        return new Ground(forces, mechs, docks);
    }

    public Footprint after(String target, Collection<Move> moves, Collection<String> gainedPlanets) {
        Map<String, Integer> newUnits = new HashMap<>(units);
        Map<String, Integer> newShips = new HashMap<>(ships);
        Map<String, Integer> newNonFighter = new HashMap<>(nonFighterShips);
        Map<String, Integer> newHeavy = new HashMap<>(heavyShips);
        for (Move move : moves) {
            if (move.origin().equals(target)) continue;
            shift(newUnits, move.origin(), target, move.count());
            if (BoardView.MOVING_SHIPS.contains(move.type()) || move.type() == UnitType.Fighter) {
                shift(newShips, move.origin(), target, move.count());
            }
            if (BoardView.MOVING_SHIPS.contains(move.type())) shift(newNonFighter, move.origin(), target, move.count());
            if (isHeavy(move.type())) shift(newHeavy, move.origin(), target, move.count());
        }
        Set<String> newPlanets = new HashSet<>(planets);
        newPlanets.addAll(gainedPlanets);
        return new Footprint(
                newUnits,
                newShips,
                newNonFighter,
                newHeavy,
                newPlanets,
                dreadnoughts,
                groundAfter(target, moves, gainedPlanets));
    }

    private Ground groundAfter(String target, Collection<Move> moves, Collection<String> landedOn) {
        Map<String, Integer> forces = new HashMap<>(ground.forces());
        Map<String, Integer> mechs = new HashMap<>(ground.mechs());
        int arrivingInfantry = 0;
        int arrivingMechs = 0;
        for (Move move : moves) {
            if (!isGroundForce(move.type())) continue;
            if (!BoardView.SPACE.equals(move.holder())) {
                take(forces, move.holder(), move.count());
                if (move.type() == UnitType.Mech) take(mechs, move.holder(), move.count());
            }
            if (move.origin().equals(target) && BoardView.SPACE.equals(move.holder())) continue;
            if (move.type() == UnitType.Mech) {
                arrivingMechs += move.count();
            } else {
                arrivingInfantry += move.count();
            }
        }
        List<String> landings = List.copyOf(landedOn);
        if (landings.isEmpty()) return new Ground(forces, mechs, ground.docks());
        for (String planet : landings) {
            if (arrivingMechs == 0) break;
            if (mechs.containsKey(planet)) continue;
            mechs.put(planet, 1);
            forces.merge(planet, 1, Integer::sum);
            arrivingMechs--;
        }
        String first = landings.getFirst();
        if (arrivingMechs > 0) mechs.merge(first, arrivingMechs, Integer::sum);
        forces.merge(first, arrivingMechs + arrivingInfantry, Integer::sum);
        return new Ground(forces, mechs, ground.docks());
    }

    private static boolean isGroundForce(UnitType type) {
        return type == UnitType.Infantry || type == UnitType.Mech;
    }

    private static void take(Map<String, Integer> counts, String key, int amount) {
        counts.computeIfPresent(key, (ignored, count) -> count > amount ? count - amount : null);
    }

    public Footprint withoutShipsIn(String position) {
        Map<String, Integer> newUnits = new HashMap<>(units);
        int leaving = ships.getOrDefault(position, 0);
        newUnits.computeIfPresent(position, (ignored, count) -> count > leaving ? count - leaving : null);
        Map<String, Integer> newShips = new HashMap<>(ships);
        Map<String, Integer> newNonFighter = new HashMap<>(nonFighterShips);
        Map<String, Integer> newHeavy = new HashMap<>(heavyShips);
        newShips.remove(position);
        newNonFighter.remove(position);
        newHeavy.remove(position);
        return new Footprint(newUnits, newShips, newNonFighter, newHeavy, planets, dreadnoughts, ground);
    }

    public Footprint withPlanet(String planet) {
        Set<String> newPlanets = new HashSet<>(planets);
        newPlanets.add(planet);
        return new Footprint(units, ships, nonFighterShips, heavyShips, newPlanets, dreadnoughts, ground);
    }

    public Footprint withoutPlanet(String planet) {
        Set<String> newPlanets = new HashSet<>(planets);
        newPlanets.remove(planet);
        return new Footprint(units, ships, nonFighterShips, heavyShips, newPlanets, dreadnoughts, ground);
    }

    public Footprint withBuilt(String position, Map<UnitType, Integer> built) {
        Map<String, Integer> newUnits = new HashMap<>(units);
        Map<String, Integer> newShips = new HashMap<>(ships);
        Map<String, Integer> newNonFighter = new HashMap<>(nonFighterShips);
        Map<String, Integer> newHeavy = new HashMap<>(heavyShips);
        built.forEach((type, count) -> {
            newUnits.merge(position, count, Integer::sum);
            if (BoardView.MOVING_SHIPS.contains(type) || type == UnitType.Fighter) {
                newShips.merge(position, count, Integer::sum);
            }
            if (BoardView.MOVING_SHIPS.contains(type)) newNonFighter.merge(position, count, Integer::sum);
            if (isHeavy(type)) newHeavy.merge(position, count, Integer::sum);
        });
        int newDreadnoughts = dreadnoughts + built.getOrDefault(UnitType.Dreadnought, 0);
        return new Footprint(newUnits, newShips, newNonFighter, newHeavy, planets, newDreadnoughts, ground);
    }

    public Set<String> heavyShipSystems() {
        return heavyShips.keySet();
    }

    public int dreadnoughtCount() {
        return dreadnoughts;
    }

    public int heavyShipCount() {
        return heavyShips.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void shift(Map<String, Integer> counts, String from, String to, int amount) {
        int left = counts.getOrDefault(from, 0) - amount;
        if (left > 0) {
            counts.put(from, left);
        } else {
            counts.remove(from);
        }
        counts.merge(to, amount, Integer::sum);
    }

    public Set<String> unitSystems() {
        return units.keySet();
    }

    public Set<String> shipSystems() {
        return ships.keySet();
    }

    public int largestFleet() {
        return nonFighterShips.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    public int shipsIn(String position) {
        return ships.getOrDefault(position, 0);
    }

    public Set<String> planets() {
        return planets;
    }

    public Set<String> mechPlanets() {
        return ground.mechs().keySet();
    }

    public int mostGroundForcesWithoutADock() {
        return ground.forces().entrySet().stream()
                .filter(entry -> !ground.docks().contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .max()
                .orElse(0);
    }
}
