package ti4.ai.strategy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.eval.FracturePrize;
import ti4.ai.explore.ExploreSite;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.tactical.SeatStrike;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class StructurePolicy {

    static final String PDS = "pds";
    static final String SPACE_DOCK = "spacedock";
    private static final String FUEL_THE_WAR_MACHINE = "fwm";
    private static final String MECHANIZE_THE_MILITARY = "mtm";
    private static final int MECH_SPREADING_PREFERENCE = 2;
    private static final int SEAT_DOCK_PREFERENCE = 2;
    private static final int MIN_FORWARD_DOCK_RESOURCES = 2;
    private static final int RICH_DOCK_RESOURCES = 3;
    private static final int SLICE_SEARCH_DEPTH = 6;
    private static final String STYX = "styx";
    private static final String SAAR_DOCK = "saar_spacedock";
    private static final String SAAR_DOCK_II = "saar_spacedock2";
    private static final Set<String> SPREAD_OBJECTIVES = Set.of("infrastructure", "protect_border");
    private static final int STRUCTURES_SHORT = 2;
    private static final int NEXT_ROUND_SHORT = 3;
    private static final String ESTABLISH_A_PERIMETER = "eap";
    private static final int PERIMETER_NEAR = 3;
    private static final int PRIMARY_STRUCTURES = 2;
    private static final int NO_SHORTFALL = Integer.MAX_VALUE;
    private static final Set<String> STRUCTURE_OBJECTIVES =
            Set.of("build_defenses", "infrastructure", "massive_cities", "protect_border", "eap", "fwm");

    static boolean wantsStructures(Game game, Player seat) {
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (STRUCTURE_OBJECTIVES.contains(id) && !ObjectivePolicy.hasScored(game, seat, id)) return true;
        }
        return seat.getSecretsUnscored().keySet().stream().anyMatch(STRUCTURE_OBJECTIVES::contains);
    }

    static boolean nearsStructureObjective(Game game, Player seat) {
        return structureShortfall(game, seat) <= NEXT_ROUND_SHORT || nearsPerimeter(game, seat);
    }

    static boolean nearsPerimeter(Game game, Player seat) {
        return perimeterShortfall(game, seat) <= PERIMETER_NEAR;
    }

    static int perimeterShortfall(Game game, Player seat) {
        if (!seat.getSecretsUnscored().containsKey(ESTABLISH_A_PERIMETER)
                || pds(game, seat).isEmpty()) {
            return NO_SHORTFALL;
        }
        int missing = shortfallOf(game, seat, ESTABLISH_A_PERIMETER);
        return missing > 0 ? missing : NO_SHORTFALL;
    }

    static double objectiveScale(Game game, Player seat) {
        double scale = closeness(structureShortfall(game, seat));
        return scale > 0 ? scale : 1;
    }

    static int structureShortfall(Game game, Player seat) {
        int shortfall =
                shortfalls(game, seat).values().stream().min(Integer::compare).orElse(NO_SHORTFALL);
        if (MassProduction.oneDockFromScoring(game, seat)) shortfall = Math.min(shortfall, 1);
        return shortfall;
    }

    private static int shortfallOf(Game game, Player seat, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        if (threshold <= 0) return 0;
        return threshold - ListPlayerInfoService.getPlayerProgressOnObjective(objective, game, seat);
    }

    static Optional<String> next(Game game, Player seat) {
        boolean fuelsWarMachine = seat.getSecretsUnscored().containsKey(FUEL_THE_WAR_MACHINE);
        if (underCap(game, seat, "sd")
                && (MassProduction.drives(game, seat)
                        || dockSite(game, seat, fuelsWarMachine).isPresent())) {
            return Optional.of(SPACE_DOCK);
        }
        return pds(game, seat);
    }

    static Optional<String> secondary(Game game, Player seat) {
        return buildsPerimeter(game, seat) ? pds(game, seat) : next(game, seat);
    }

    static Optional<String> primaryFirst(Game game, Player seat, int round) {
        int perimeter = perimeterShortfall(game, seat);
        boolean closesNow = perimeter <= PRIMARY_STRUCTURES;
        boolean docksDone = round > CardValueScale.LAST_EARLY_DOCK_ROUND;
        boolean pdsFirst = buildsPerimeter(game, seat) && (closesNow || docksDone);
        return pdsFirst ? pds(game, seat) : next(game, seat);
    }

    private static boolean buildsPerimeter(Game game, Player seat) {
        int perimeter = perimeterShortfall(game, seat);
        return perimeter != NO_SHORTFALL && !closerDockObjective(game, seat, perimeter);
    }

    private static boolean closerDockObjective(Game game, Player seat, int perimeter) {
        boolean dockNext = next(game, seat).filter(SPACE_DOCK::equals).isPresent();
        return dockNext && competingDockShortfall(game, seat) < perimeter;
    }

    private static int competingDockShortfall(Game game, Player seat) {
        int shortfall = MassProduction.docksNeeded(game, seat);
        if (seat.getSecretsUnscored().containsKey(FUEL_THE_WAR_MACHINE)) {
            int missing = shortfallOf(game, seat, FUEL_THE_WAR_MACHINE);
            if (missing > 0) shortfall = Math.min(shortfall, missing);
        }
        return shortfall;
    }

    static Optional<String> pds(Game game, Player seat) {
        return underCap(game, seat, "pds") ? Optional.of(PDS) : Optional.empty();
    }

    record Placement(String unit, String planet, double value) {}

    private record DockRank(
            boolean canHost,
            int mass,
            boolean site,
            boolean fresh,
            int preference,
            int resources,
            int outside,
            int systemResources,
            int influence) {}

    private static final Comparator<DockRank> USUAL_DOCK = Comparator.comparing(DockRank::site)
            .thenComparing(DockRank::fresh)
            .thenComparingInt(DockRank::preference)
            .thenComparingInt(DockRank::resources)
            .thenComparingInt(DockRank::outside)
            .thenComparingInt(DockRank::systemResources)
            .thenComparingInt(DockRank::influence);

    private static final Comparator<DockRank> MASS_PRODUCTION_DOCK = Comparator.comparing(DockRank::canHost)
            .thenComparingInt(DockRank::mass)
            .thenComparing(USUAL_DOCK);

    static Optional<String> planetFor(Game game, Player seat, String unit, Collection<String> candidates) {
        List<Planet> planets = candidates.stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(planet -> planet != null && seat.getPlanets().contains(planet.getName()))
                .toList();
        return bestPlanet(game, seat, unit, planets).map(Planet::getName);
    }

    private static Optional<Planet> bestPlanet(Game game, Player seat, String unit, List<Planet> planets) {
        if (PDS.equals(unit)) return PdsSites.of(game, seat, planets).best();
        boolean mass = MassProduction.drives(game, seat);
        Map<Planet, DockRank> ranks = new IdentityHashMap<>();
        List<Planet> legal =
                planets.stream().filter(planet -> dockLegal(game, seat, planet)).toList();
        boolean spread = spreadsObjectives(game, seat);
        for (Planet planet : legal) ranks.put(planet, dockRank(game, seat, planet, mass, spread));
        Comparator<DockRank> order = mass ? MASS_PRODUCTION_DOCK : USUAL_DOCK;
        return legal.stream().max(Comparator.comparing(ranks::get, order));
    }

    private static boolean dockLegal(Game game, Player seat, Planet planet) {
        return BoardView.count(planet, seat, UnitType.Spacedock) == 0
                && !planet.isSpaceStation(game)
                && !ExploreSite.isDemilitarized(planet);
    }

    private static DockRank dockRank(Game game, Player seat, Planet planet, boolean mass, boolean spread) {
        boolean canHost = mass && MassProduction.canHostDock(game, seat, planet);
        return new DockRank(
                canHost,
                mass ? MassProduction.productionWithDockOn(game, seat, planet) : 0,
                isDockSite(game, seat, planet),
                spread && freshOutsideHome(game, seat, planet),
                dockPreference(game, seat, planet),
                planet.getResources(),
                isHome(game, seat, planet) ? 0 : 1,
                systemResources(game, seat, planet),
                planet.getInfluence());
    }

    private static Optional<Planet> dockSite(Game game, Player seat, boolean anyPlanet) {
        return seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(planet -> planet != null && BoardView.count(planet, seat, UnitType.Spacedock) == 0)
                .filter(planet -> anyPlanet || isDockSite(game, seat, planet))
                .findAny();
    }

    static boolean isDockSite(Game game, Player seat, Planet planet) {
        if (!dockLegal(game, seat, planet)) return false;
        if (!isHome(game, seat, planet)) return isForwardDockSite(game, seat, planet);
        return secondHomeDockWanted(game, seat, planet);
    }

    private static boolean isForwardDockSite(Game game, Player seat, Planet planet) {
        return planet.getResources() >= MIN_FORWARD_DOCK_RESOURCES
                || onOwnIngress(game, seat, planet)
                || SeatStrike.wantsDockOn(game, seat, planet);
    }

    private static boolean secondHomeDockWanted(Game game, Player seat, Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null) return false;
        long held = tile.getPlanetUnitHolders().stream()
                .filter(other -> seat.getPlanets().contains(other.getName()))
                .count();
        if (held < 2) return false;
        return MassProduction.drives(game, seat) || homeProductionLimited(game, seat, tile, planet);
    }

    private static boolean homeProductionLimited(Game game, Player seat, Tile home, Planet planet) {
        int spendable = seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(Objects::nonNull)
                .mapToInt(Planet::getResources)
                .sum();
        if (spendable <= Helper.getProductionValue(seat, game, home, false)) return false;
        return planet.getResources() >= bestForwardDockResources(game, seat);
    }

    private static int bestForwardDockResources(Game game, Player seat) {
        return seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(Objects::nonNull)
                .filter(planet -> !isHome(game, seat, planet) && dockLegal(game, seat, planet))
                .filter(planet -> isForwardDockSite(game, seat, planet))
                .mapToInt(Planet::getResources)
                .max()
                .orElse(0);
    }

    private static int dockPreference(Game game, Player seat, Planet planet) {
        if (isHome(game, seat, planet)) return 1;
        int rich = planet.getResources() >= RICH_DOCK_RESOURCES ? 1 : 0;
        int nearby = STYX.equals(planet.getName()) || inSlice(game, seat, planet) ? 1 : 0;
        int ingress = onOwnIngress(game, seat, planet) ? 2 : 0;
        int seatDock = SeatStrike.wantsDockOn(game, seat, planet) ? SEAT_DOCK_PREFERENCE : 0;
        return rich + nearby + ingress + seatDock + mechSpreadingSite(game, seat, planet);
    }

    private static boolean onOwnIngress(Game game, Player seat, Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile != null
                && FracturePrize.hasIngress(tile)
                && !tile.isMecatol(game)
                && FractureCampaign.styxToTake(game, seat)
                && inSlice(game, seat, planet);
    }

    private static int mechSpreadingSite(Game game, Player seat, Planet planet) {
        if (!seat.getSecretsUnscored().containsKey(MECHANIZE_THE_MILITARY)) return 0;
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null) return 0;
        boolean anotherHeldPlanet = tile.getPlanetUnitHolders().stream()
                .anyMatch(other -> other != planet && seat.getPlanets().contains(other.getName()));
        return anotherHeldPlanet ? MECH_SPREADING_PREFERENCE : 0;
    }

    // TODO: could use ti4.ai.eval.Territory instead of its own slice search
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

    static int structures(Player seat, Planet planet) {
        return BoardView.count(planet, seat, UnitType.Pds) + BoardView.count(planet, seat, UnitType.Spacedock);
    }

    static boolean isHome(Game game, Player seat, Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null) return true;
        Tile home = seat.getHomeSystemTile();
        return home != null && home.getPosition().equals(tile.getPosition());
    }

    static boolean freshOutsideHome(Game game, Player seat, Planet planet) {
        return !isHome(game, seat, planet) && structures(seat, planet) == 0;
    }

    static boolean spreadsObjectives(Game game, Player seat) {
        Map<String, Integer> shortfalls = shortfalls(game, seat);
        return SPREAD_OBJECTIVES.stream().anyMatch(shortfalls::containsKey);
    }

    static Optional<Placement> bestSecondary(Game game, Player seat) {
        List<Placement> options = new ArrayList<>();
        if (!buildsPerimeter(game, seat)) dockPlacement(game, seat).ifPresent(options::add);
        pdsPlacement(game, seat).ifPresent(options::add);
        return options.stream().max(Comparator.comparingDouble(Placement::value));
    }

    static boolean cheapDockWorthFollowing(Game game, Player seat) {
        if (!canBuildDock(game, seat) || dockSite(game, seat, false).isEmpty()) return false;
        return dockBaseValue(game, seat) - CardValueScale.STRATEGY_TOKEN >= CardValueScale.CONSTRUCTION_FOLLOW_MARGIN;
    }

    private static Optional<Placement> dockPlacement(Game game, Player seat) {
        if (!canBuildDock(game, seat)) return Optional.empty();
        Optional<Planet> planet = bestPlanet(game, seat, SPACE_DOCK, ownedPlanets(game, seat));
        if (planet.isEmpty()) return Optional.empty();
        double base = isDockSite(game, seat, planet.get()) ? dockBaseValue(game, seat) : 0;
        double value = base + objectiveGain(game, seat, SPACE_DOCK, planet.get());
        return value > 0 ? Optional.of(new Placement(SPACE_DOCK, planet.get().getName(), value)) : Optional.empty();
    }

    private static Optional<Placement> pdsPlacement(Game game, Player seat) {
        if (pds(game, seat).isEmpty()) return Optional.empty();
        PdsSites sites = PdsSites.of(game, seat, ownedPlanets(game, seat));
        Optional<Planet> planet = sites.best();
        if (planet.isEmpty()) return Optional.empty();
        double value = CardValueScale.PDS
                + (sites.threatened(planet.get()) ? CardValueScale.THREATENED_PDS : 0)
                + (sites.frontier(planet.get()) ? CardValueScale.FRONTIER_PDS : 0)
                + objectiveGain(game, seat, PDS, planet.get());
        return Optional.of(new Placement(PDS, planet.get().getName(), value));
    }

    private static boolean canBuildDock(Game game, Player seat) {
        boolean floatingFactory = seat.hasUnit(SAAR_DOCK) || seat.hasUnit(SAAR_DOCK_II);
        return !floatingFactory && underCap(game, seat, "sd");
    }

    private static double dockBaseValue(Game game, Player seat) {
        double base = game.getRound() <= CardValueScale.LAST_EARLY_DOCK_ROUND
                ? CardValueScale.DOCK_EARLY
                : CardValueScale.DOCK_LATE;
        int extraDocks = Math.max(0, ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, "sd") - 1);
        return base / (1 + extraDocks);
    }

    private static List<Planet> ownedPlanets(Game game, Player seat) {
        return seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(Objects::nonNull)
                .toList();
    }

    private static double objectiveGain(Game game, Player seat, String unit, Planet planet) {
        double closeness = 0;
        for (Map.Entry<String, Integer> shortfall : shortfalls(game, seat).entrySet()) {
            if (advances(game, seat, shortfall.getKey(), unit, planet)) {
                closeness = Math.max(closeness, closeness(shortfall.getValue()));
            }
        }
        if (SPACE_DOCK.equals(unit) && MassProduction.oneDockFromScoring(game, seat)) closeness = 1;
        return closeness * CardValueScale.STRUCTURE_OBJECTIVE_SHARE * CardValueScale.VICTORY_POINT;
    }

    private static boolean advances(Game game, Player seat, String objective, String unit, Planet planet) {
        return switch (objective) {
            case ESTABLISH_A_PERIMETER -> PDS.equals(unit);
            case FUEL_THE_WAR_MACHINE -> SPACE_DOCK.equals(unit);
            case "infrastructure", "protect_border" -> freshOutsideHome(game, seat, planet);
            default -> true;
        };
    }

    private static double closeness(int missing) {
        if (missing <= STRUCTURES_SHORT) return 1;
        return missing <= NEXT_ROUND_SHORT ? (double) STRUCTURES_SHORT / missing : 0;
    }

    private static Map<String, Integer> shortfalls(Game game, Player seat) {
        Map<String, Integer> shortfalls = new LinkedHashMap<>();
        for (String id : heldObjectives(game, seat)) {
            int missing = shortfallOf(game, seat, id);
            if (missing > 0) shortfalls.merge(id, missing, Math::min);
        }
        return shortfalls;
    }

    private static List<String> heldObjectives(Game game, Player seat) {
        List<String> objectives = new ArrayList<>();
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (STRUCTURE_OBJECTIVES.contains(id) && !ObjectivePolicy.hasScored(game, seat, id)) objectives.add(id);
        }
        seat.getSecretsUnscored().keySet().stream()
                .filter(STRUCTURE_OBJECTIVES::contains)
                .forEach(objectives::add);
        if (pds(game, seat).isEmpty()) objectives.remove(ESTABLISH_A_PERIMETER);
        return objectives;
    }

    private static boolean underCap(Game game, Player seat, String asyncId) {
        int cap = seat.getUnitCap(asyncId);
        return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId) < cap;
    }
}
