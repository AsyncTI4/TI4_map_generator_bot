package ti4.ai.strategy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class StructurePolicy {

    static final String PDS = "pds";
    static final String SPACE_DOCK = "spacedock";
    private static final String FUEL_THE_WAR_MACHINE = "fwm";
    private static final int MAX_PDS_PER_PLANET = 2;
    private static final int MIN_FORWARD_DOCK_RESOURCES = 2;
    private static final int RICH_DOCK_RESOURCES = 3;
    private static final int SLICE_SEARCH_DEPTH = 6;
    private static final String STYX = "styx";
    private static final int STRUCTURES_SHORT = 2;
    private static final Set<String> STRUCTURE_OBJECTIVES =
            Set.of("build_defenses", "infrastructure", "massive_cities", "protect_border", "eap", "fwm");

    static boolean wantsStructures(Game game, Player seat) {
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (STRUCTURE_OBJECTIVES.contains(id) && !ObjectivePolicy.hasScored(game, seat, id)) return true;
        }
        return seat.getSecretsUnscored().keySet().stream().anyMatch(STRUCTURE_OBJECTIVES::contains);
    }

    static boolean nearsStructureObjective(Game game, Player seat) {
        List<String> objectives = new ArrayList<>();
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (STRUCTURE_OBJECTIVES.contains(id) && !ObjectivePolicy.hasScored(game, seat, id)) objectives.add(id);
        }
        seat.getSecretsUnscored().keySet().stream()
                .filter(STRUCTURE_OBJECTIVES::contains)
                .forEach(objectives::add);
        for (String id : objectives) {
            int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
            if (threshold <= 0) continue;
            int missing = threshold - ListPlayerInfoService.getPlayerProgressOnObjective(id, game, seat);
            if (missing > 0 && missing <= STRUCTURES_SHORT) return true;
        }
        return false;
    }

    static Optional<String> next(Game game, Player seat) {
        boolean fuelsWarMachine = seat.getSecretsUnscored().containsKey(FUEL_THE_WAR_MACHINE);
        if (underCap(game, seat, "sd") && dockSite(game, seat, fuelsWarMachine).isPresent()) {
            return Optional.of(SPACE_DOCK);
        }
        return pds(game, seat);
    }

    static Optional<String> pds(Game game, Player seat) {
        return underCap(game, seat, "pds") ? Optional.of(PDS) : Optional.empty();
    }

    static Optional<String> planetFor(Game game, Player seat, String unit, Collection<String> candidates) {
        Comparator<Planet> preference = SPACE_DOCK.equals(unit)
                ? Comparator.comparing((Planet planet) -> isDockSite(game, seat, planet))
                        .thenComparingInt(planet -> dockPreference(game, seat, planet))
                        .thenComparingInt(Planet::getResources)
                        .thenComparingInt(planet -> isHome(game, planet) ? 0 : 1)
                        .thenComparingInt(planet -> systemResources(game, seat, planet))
                        .thenComparingInt(Planet::getInfluence)
                : Comparator.comparingInt((Planet planet) -> isHome(game, planet) ? 0 : 1)
                        .thenComparingInt(planet -> -structures(seat, planet))
                        .thenComparingInt(planet -> planet.getResources() + planet.getInfluence());
        return candidates.stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(planet -> planet != null && seat.getPlanets().contains(planet.getName()))
                .filter(planet -> !PDS.equals(unit) || BoardView.count(planet, seat, UnitType.Pds) < MAX_PDS_PER_PLANET)
                .max(preference)
                .map(Planet::getName);
    }

    private static Optional<Planet> dockSite(Game game, Player seat, boolean anyPlanet) {
        return seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(planet -> planet != null && BoardView.count(planet, seat, UnitType.Spacedock) == 0)
                .filter(planet -> anyPlanet || isDockSite(game, seat, planet))
                .findAny();
    }

    private static boolean isDockSite(Game game, Player seat, Planet planet) {
        if (BoardView.count(planet, seat, UnitType.Spacedock) > 0 || planet.isSpaceStation(game)) return false;
        if (!isHome(game, planet)) return planet.getResources() >= MIN_FORWARD_DOCK_RESOURCES;
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile != null
                && tile.getPlanetUnitHolders().stream()
                                .filter(other -> seat.getPlanets().contains(other.getName()))
                                .count()
                        > 1;
    }

    private static int dockPreference(Game game, Player seat, Planet planet) {
        if (isHome(game, planet)) return 1;
        int rich = planet.getResources() >= RICH_DOCK_RESOURCES ? 1 : 0;
        int nearby = STYX.equals(planet.getName()) || inSlice(game, seat, planet) ? 1 : 0;
        return rich + nearby;
    }

    private static boolean inSlice(Game game, Player seat, Planet planet) {
        Tile home = seat.getHomeSystemTile();
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (home == null || tile == null) return false;
        Map<String, Integer> fromHome = distances(game, seat, home.getPosition());
        Integer homeDistance = fromHome.get(tile.getPosition());
        if (homeDistance == null) return false;
        if (homeDistance <= 1) return true;
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null) return false;
        Integer toMecatol = distances(game, seat, mecatol.getPosition()).get(tile.getPosition());
        Integer homeToMecatol = fromHome.get(mecatol.getPosition());
        return toMecatol != null && homeToMecatol != null && homeDistance + toMecatol == homeToMecatol;
    }

    private static Map<String, Integer> distances(Game game, Player seat, String origin) {
        Map<String, Integer> distances = new HashMap<>();
        distances.put(origin, 0);
        Deque<String> queue = new ArrayDeque<>(List.of(origin));
        while (!queue.isEmpty()) {
            String position = queue.removeFirst();
            int distance = distances.get(position);
            if (distance >= SLICE_SEARCH_DEPTH) continue;
            for (String next : FoWHelper.getAdjacentTiles(game, position, seat, false, false)) {
                if (distances.putIfAbsent(next, distance + 1) == null) queue.addLast(next);
            }
        }
        return distances;
    }

    private static int systemResources(Game game, Player seat, Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null) return 0;
        return tile.getPlanetUnitHolders().stream()
                .filter(other -> seat.getPlanets().contains(other.getName()))
                .mapToInt(Planet::getResources)
                .sum();
    }

    private static int structures(Player seat, Planet planet) {
        return BoardView.count(planet, seat, UnitType.Pds) + BoardView.count(planet, seat, UnitType.Spacedock);
    }

    private static boolean isHome(Game game, Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile == null || tile.isHomeSystem(game);
    }

    private static boolean underCap(Game game, Player seat, String asyncId) {
        int cap = seat.getUnitCap(asyncId);
        return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId) < cap;
    }
}
