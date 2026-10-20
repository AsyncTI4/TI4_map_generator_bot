package ti4.ai.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Territory;
import ti4.ai.eval.Territory.Zone;
import ti4.ai.explore.ExploreSite;
import ti4.ai.tactical.DefenseOutlook;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

final class PdsSites {

    private static final int MAX_PDS_PER_PLANET = 2;
    private static final String PDS_TWO = "pds2";
    private static final Set<Zone> FRONTIER_ZONES =
            EnumSet.of(Zone.LANE, Zone.BORDER, Zone.FOREIGN, Zone.OTHER_HOME, Zone.MECATOL);

    private record Rank(
            boolean fresh,
            boolean defendsHome,
            boolean outside,
            boolean empty,
            boolean frontier,
            boolean uncovered,
            int value) {}

    private static final Comparator<Rank> BEST = Comparator.comparing(Rank::fresh)
            .thenComparing(Rank::defendsHome)
            .thenComparing(Rank::outside)
            .thenComparing(Rank::empty)
            .thenComparing(Rank::frontier)
            .thenComparing(Rank::uncovered)
            .thenComparingInt(Rank::value);

    private final Game game;
    private final Player seat;
    private final List<Planet> planets;
    private final Map<String, Set<String>> adjacent = new HashMap<>();
    private Territory territory;
    private final boolean spreads;
    private Boolean homeThreatened;

    private PdsSites(Game game, Player seat, List<Planet> planets) {
        this.game = game;
        this.seat = seat;
        this.planets = planets;
        this.spreads = StructurePolicy.spreadsObjectives(game, seat);
    }

    static PdsSites of(Game game, Player seat, Collection<Planet> candidates) {
        return new PdsSites(
                game,
                seat,
                candidates.stream().filter(planet -> legal(game, seat, planet)).toList());
    }

    static boolean legal(Game game, Player seat, Planet planet) {
        if (BoardView.count(planet, seat, UnitType.Pds) >= MAX_PDS_PER_PLANET) return false;
        if (planet.isSpaceStation(game) || ExploreSite.isDemilitarized(planet)) return false;
        boolean hostile =
                BoardView.enemyGroundForcesOn(game, seat, planet) || BoardView.enemyStructuresOn(game, seat, planet);
        return !hostile || BoardView.groundForces(planet, seat) > 0;
    }

    Optional<Planet> best() {
        Map<Planet, Rank> ranks = new IdentityHashMap<>();
        for (Planet planet : planets) ranks.put(planet, rank(planet));
        return planets.stream().max(Comparator.comparing(ranks::get, BEST));
    }

    boolean threatened(Planet planet) {
        if (StructurePolicy.isHome(game, seat, planet)) return homeThreatened();
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile != null && enemyShipsNear(tile);
    }

    boolean frontier(Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null) return false;
        return FRONTIER_ZONES.contains(territory().zone(tile))
                || enemyShipsNear(tile)
                || enemyPlanetNear(tile)
                || coversForwardDock(tile);
    }

    private Rank rank(Planet planet) {
        boolean home = StructurePolicy.isHome(game, seat, planet);
        int own = StructurePolicy.structures(seat, planet);
        Tile tile = game.getTileFromPlanet(planet.getName());
        return new Rank(
                spreads && StructurePolicy.freshOutsideHome(game, seat, planet),
                home && homeThreatened(),
                !home,
                own == 0,
                frontier(planet),
                tile == null || BoardView.countInTile(tile, seat, UnitType.Pds) == 0,
                planet.getResources() + planet.getInfluence());
    }

    private boolean homeThreatened() {
        if (homeThreatened == null) homeThreatened = DefenseOutlook.homeCost(game, seat, false) > 0;
        return homeThreatened;
    }

    private Territory territory() {
        if (territory == null) territory = Territory.of(game, seat);
        return territory;
    }

    private Set<String> adjacentTo(Tile tile) {
        return adjacent.computeIfAbsent(
                tile.getPosition(), position -> FoWHelper.getAdjacentTiles(game, position, seat, false, false));
    }

    private boolean enemyShipsNear(Tile tile) {
        if (BoardView.hasEnemyShips(game, seat, tile)) return true;
        return adjacentTo(tile).stream()
                .map(game::getTileByPosition)
                .anyMatch(next -> next != null && BoardView.hasEnemyShips(game, seat, next));
    }

    private boolean enemyPlanetNear(Tile tile) {
        return adjacentTo(tile).stream()
                .map(game::getTileByPosition)
                .anyMatch(next -> next != null && holdsEnemyPlanet(next));
    }

    private boolean holdsEnemyPlanet(Tile tile) {
        return BoardView.planets(tile).stream().anyMatch(planet -> {
            Player controller = BoardView.controller(game, planet.getName());
            return controller != null && controller != seat;
        });
    }

    private boolean coversForwardDock(Tile tile) {
        if (hasForwardDock(tile)) return true;
        if (!seat.hasTech(PDS_TWO)) return false;
        return adjacentTo(tile).stream()
                .map(game::getTileByPosition)
                .anyMatch(next -> next != null && hasForwardDock(next));
    }

    private boolean hasForwardDock(Tile tile) {
        Tile home = seat.getHomeSystemTile();
        boolean outside = home == null || !home.getPosition().equals(tile.getPosition());
        return outside && BoardView.countInTile(tile, seat, UnitType.Spacedock) > 0;
    }
}
