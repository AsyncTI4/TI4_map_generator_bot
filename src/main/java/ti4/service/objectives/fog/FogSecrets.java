package ti4.service.objectives.fog;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.model.WormholeModel.Wormhole;
import ti4.service.objectives.SecretObjectiveDeckService;

@UtilityClass
public class FogSecrets {

    public static final String DECK = "secret_objectives_fog";
    static final int ROUND_GATE = 3;

    static final String FIND_MECATOL = "findmecatolfog";
    static final String SURVEY_ROCKS = "surveyrocksfog";
    static final String WORMHOLE_CARTOGRAPHER = "wormholecartofog";
    static final String CATALOGUE_ANOMALIES = "cataloganomaliesfog";
    static final String FIND_THRONES = "findthronesfog";
    static final String EDGE_OF_MAP = "edgeofmapfog";
    static final String CHART_EXPANSE = "chartexpansefog";
    static final String MASTER_CARTOGRAPHER = "mastercartofog";
    static final String KNOW_MORE = "knowmorefog";
    static final String RUN_SILENT = "runsilentfog";
    static final String GHOST_FLEET = "ghostfleetfog";
    static final String HIDDEN_SHIPYARD = "hiddenshipyardfog";
    static final String DARK_COLONY = "darkcolonyfog";
    static final String SHROUD_THRONE = "shroudthronefog";
    static final String SECRET_PASSAGE = "secretpassagefog";
    static final String LURK_NEBULA = "lurknebulafog";
    static final String EYES_THRONE = "eyesthronefog";
    static final String WATCH_LEADER = "watchleaderfog";
    static final String SHADOW_RIVALS = "shadowrivalsfog";
    static final String MARK_FLAGSHIPS = "markflagshipsfog";
    static final String WIDE_NET = "widenetfog";

    private static final Set<UnitType> CAPITAL_SHIPS =
            EnumSet.of(UnitType.Flagship, UnitType.Warsun, UnitType.Dreadnought, UnitType.Carrier);

    public static boolean isFogSecret(String soID) {
        return Mapper.getDeck(DECK).getNewDeck().contains(soID);
    }

    public static void setLoaded(Game game, boolean loaded) {
        List<String> fogSecrets = Mapper.getDeck(DECK).getNewDeck();
        if (loaded) {
            SecretObjectiveDeckService.shuffleIntoDeck(game, fogSecrets);
        } else {
            SecretObjectiveDeckService.removeFromDeck(game, fogSecrets);
        }
    }

    public static int threshold(String soID, Game game) {
        return switch (soID) {
            case FIND_MECATOL, MASTER_CARTOGRAPHER, HIDDEN_SHIPYARD, SHROUD_THRONE, SECRET_PASSAGE, WATCH_LEADER -> 1;
            case SURVEY_ROCKS -> mapScaledTarget(countSystems(game, Tile::isAsteroidField), 8);
            case WORMHOLE_CARTOGRAPHER ->
                mapScaledTarget(wormholeTypes(game, allSystemPositions(game)).size(), 2);
            case CATALOGUE_ANOMALIES ->
                Math.max(1, anomalyTypes(game, allSystemPositions(game)).size());
            case FIND_THRONES, DARK_COLONY, RUN_SILENT, LURK_NEBULA -> 3;
            case EDGE_OF_MAP, SHADOW_RIVALS -> 4;
            case CHART_EXPANSE ->
                Math.max(1, ceilDiv(2 * FogSight.countableSystems(game).size(), 3));
            case KNOW_MORE, EYES_THRONE, MARK_FLAGSHIPS -> 2;
            case GHOST_FLEET, WIDE_NET -> 5;
            default -> 0;
        };
    }

    public static int progress(String soID, Game game, Player player) {
        return switch (soID) {
            case FIND_MECATOL -> asProgress(foundMecatol(game, player));
            case SURVEY_ROCKS -> countFound(game, player, Tile::isAsteroidField);
            case WORMHOLE_CARTOGRAPHER ->
                wormholeTypes(game, FogSight.found(game, player)).size();
            case CATALOGUE_ANOMALIES ->
                anomalyTypes(game, FogSight.found(game, player)).size();
            case FIND_THRONES -> foundHomeSystems(game, player);
            case EDGE_OF_MAP -> countFound(game, player, tile -> tile.isEdgeOfBoard(game));
            case CHART_EXPANSE -> gated(game, FogSight.found(game, player).size(), threshold(soID, game));
            case MASTER_CARTOGRAPHER -> gated(game, asProgress(isMasterCartographer(game, player)), 1);
            case KNOW_MORE -> gated(game, playersOutscouted(game, player), 2);
            case RUN_SILENT -> gated(game, unseenShips(game, player, CAPITAL_SHIPS::contains, false), 3);
            case GHOST_FLEET -> gated(game, unseenShips(game, player, type -> type != UnitType.Fighter, true), 5);
            case HIDDEN_SHIPYARD -> asProgress(hasHiddenShipyard(game, player));
            case DARK_COLONY -> hiddenColonies(game, player);
            case SHROUD_THRONE -> gated(game, asProgress(homeIsHidden(game, player)), 1);
            case SECRET_PASSAGE -> asProgress(holdsPrivatePassage(game, player));
            case LURK_NEBULA -> largestHiddenNebulaFleet(game, player);
            case EYES_THRONE -> homeSystemsInView(game, player);
            case WATCH_LEADER -> asProgress(watchesLeader(game, player));
            case SHADOW_RIVALS -> rivalsInView(game, player);
            case MARK_FLAGSHIPS -> flagshipsInView(game, player);
            case WIDE_NET -> spreadSystems(game, player, 5);
            default -> 0;
        };
    }

    static int mapScaledTarget(int total, int divisor) {
        if (total <= 0) return 1;
        return Math.min(total, Math.max(2, ceilDiv(total, divisor)));
    }

    private static int gated(Game game, int progress, int threshold) {
        if (game.getRound() >= ROUND_GATE) return progress;
        return Math.min(progress, threshold - 1);
    }

    private static int asProgress(boolean met) {
        return met ? 1 : 0;
    }

    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    private static Set<String> allSystemPositions(Game game) {
        Set<String> positions = new HashSet<>();
        FogSight.countableSystems(game).forEach(tile -> positions.add(tile.getPosition()));
        return positions;
    }

    private static int countSystems(Game game, Predicate<Tile> filter) {
        return (int) FogSight.countableSystems(game).stream().filter(filter).count();
    }

    private static int countFound(Game game, Player player, Predicate<Tile> filter) {
        return (int) FogSight.found(game, player).stream()
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .filter(filter)
                .count();
    }

    private static boolean foundMecatol(Game game, Player player) {
        Tile mecatol = game.getMecatolTile();
        return mecatol != null && FogSight.found(game, player).contains(mecatol.getPosition());
    }

    private static Set<Wormhole> wormholeTypes(Game game, Set<String> positions) {
        Set<Wormhole> types = new HashSet<>();
        for (String position : positions) {
            Tile tile = game.getTileByPosition(position);
            if (tile != null) types.addAll(tile.getWormholes(game));
        }
        return types;
    }

    private static Set<String> anomalyTypes(Game game, Set<String> positions) {
        Set<String> types = new HashSet<>();
        for (String position : positions) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) continue;
            if (tile.isAsteroidField()) types.add("asteroid field");
            if (tile.isNebula(game)) types.add("nebula");
            if (tile.isGravityRift(game, null)) types.add("gravity rift");
            if (tile.isSupernova()) types.add("supernova");
            if (tile.isScar(game)) types.add("entropic scar");
        }
        return types;
    }

    private static int foundHomeSystems(Game game, Player player) {
        Set<String> found = FogSight.found(game, player);
        return (int) FogSight.others(game, player).stream()
                .map(FogSight::homePosition)
                .filter(found::contains)
                .count();
    }

    private static boolean isMasterCartographer(Game game, Player player) {
        int mine = FogSight.found(game, player).size();
        int half = ceilDiv(FogSight.countableSystems(game).size(), 2);
        if (mine < half) return false;
        return FogSight.others(game, player).stream()
                .allMatch(other -> FogSight.found(game, other).size() < mine);
    }

    private static int playersOutscouted(Game game, Player player) {
        Set<String> found = FogSight.found(game, player);
        String myHome = FogSight.homePosition(player);
        int count = 0;
        for (Player other : FogSight.others(game, player)) {
            String theirHome = FogSight.homePosition(other);
            if (theirHome == null || !found.contains(theirHome)) continue;
            if (myHome == null || !FogSight.found(game, other).contains(myHome)) count++;
        }
        return count;
    }

    private static int unseenShips(Game game, Player player, Predicate<UnitType> counted, boolean outsideHome) {
        Tile home = player.getHomeSystemTile();
        List<String> positions = new ArrayList<>();
        int ships = 0;
        for (Tile tile : game.getTileMap().values()) {
            if (outsideHome && tile == home) continue;
            int here = shipsIn(player, tile, counted);
            if (here == 0) continue;
            ships += here;
            positions.add(tile.getPosition());
        }
        if (FogSight.seenByAny(FogSight.visionOfOthers(game, player), positions)) return 0;
        return ships;
    }

    static int shipsIn(Player player, Tile tile, Predicate<UnitType> counted) {
        UnitHolder space = tile.getSpaceUnitHolder();
        if (space == null) return 0;
        int ships = 0;
        for (UnitKey key : space.getUnitKeysForPlayer(player)) {
            UnitModel model = player.getUnitFromUnitKey(key);
            if (model != null && model.getIsShip() && counted.test(key.unitType())) {
                ships += space.getUnitCount(key);
            }
        }
        return ships;
    }

    private static boolean hasHiddenShipyard(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        Map<Player, Set<String>> vision = FogSight.visionOfOthers(game, player);
        return game.getTileMap().values().stream()
                .filter(tile -> tile != home)
                .filter(tile -> tile.getUnitHolders().values().stream()
                        .anyMatch(holder -> holder.getUnitCount(UnitType.Spacedock, player) > 0))
                .anyMatch(tile -> !FogSight.seenByAny(vision, tile.getPosition()));
    }

    private static int hiddenColonies(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        Map<Player, Set<String>> vision = FogSight.visionOfOthers(game, player);
        int count = 0;
        for (String planet : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile == null || tile == home) continue;
            if (!FogSight.seenByAny(vision, tile.getPosition())) count++;
        }
        return count;
    }

    private static boolean homeIsHidden(Game game, Player player) {
        String home = FogSight.homePosition(player);
        return home != null && !FogSight.seenByAny(FogSight.visionOfOthers(game, player), home);
    }

    private static boolean holdsPrivatePassage(Game game, Player player) {
        Map<Player, Set<String>> vision = FogSight.visionOfOthers(game, player);
        List<Tile> hiddenWormholeSystems = game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .filter(tile -> !tile.getWormholes(game).isEmpty())
                .filter(tile -> !FogSight.seenByAny(vision, tile.getPosition()))
                .toList();
        for (int i = 0; i < hiddenWormholeSystems.size(); i++) {
            Set<Wormhole> first = new HashSet<>(hiddenWormholeSystems.get(i).getWormholes(game));
            for (int j = i + 1; j < hiddenWormholeSystems.size(); j++) {
                if (hiddenWormholeSystems.get(j).getWormholes(game).stream().anyMatch(first::contains)) return true;
            }
        }
        return false;
    }

    private static int largestHiddenNebulaFleet(Game game, Player player) {
        Map<Player, Set<String>> vision = FogSight.visionOfOthers(game, player);
        return game.getTileMap().values().stream()
                .filter(tile -> tile.isNebula(game))
                .filter(tile -> !FogSight.seenByAny(vision, tile.getPosition()))
                .mapToInt(tile -> shipsIn(player, tile, type -> type != UnitType.Fighter))
                .max()
                .orElse(0);
    }

    private static int homeSystemsInView(Game game, Player player) {
        Set<String> visible = FogSight.visible(game, player);
        return (int) FogSight.others(game, player).stream()
                .map(FogSight::homePosition)
                .filter(visible::contains)
                .count();
    }

    private static boolean watchesLeader(Game game, Player player) {
        int highest = game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(0);
        if (highest < 3) return false;
        Set<String> visible = FogSight.visible(game, player);
        return FogSight.others(game, player).stream()
                .filter(other -> other.getTotalVictoryPoints() == highest)
                .map(FogSight::homePosition)
                .anyMatch(visible::contains);
    }

    private static int rivalsInView(Game game, Player player) {
        List<Tile> inView = tilesInView(game, player);
        return (int) FogSight.others(game, player).stream()
                .filter(other -> inView.stream().anyMatch(tile -> FoWHelper.playerHasUnitsInSystem(other, tile)))
                .count();
    }

    private static int flagshipsInView(Game game, Player player) {
        List<Tile> inView = tilesInView(game, player);
        return (int) FogSight.others(game, player).stream()
                .filter(other -> inView.stream()
                        .anyMatch(tile -> tile.getSpaceUnitHolder() != null
                                && tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, other) > 0))
                .count();
    }

    private static List<Tile> tilesInView(Game game, Player player) {
        return FogSight.visible(game, player).stream()
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .toList();
    }

    static int spreadSystems(Game game, Player player, int target) {
        List<String> positions = game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                .map(Tile::getPosition)
                .toList();
        Adjacency adjacency = Adjacency.of(game, player).forEffects();
        return largestSpread(positions, adjacency, 0, new ArrayList<>(), target, 0);
    }

    private static int largestSpread(
            List<String> positions, Adjacency adjacency, int index, List<String> chosen, int target, int best) {
        best = Math.max(best, chosen.size());
        if (best >= target || index >= positions.size()) return best;
        if (chosen.size() + positions.size() - index <= best) return best;
        String candidate = positions.get(index);
        if (chosen.stream()
                .noneMatch(
                        picked -> adjacency.isAdjacent(picked, candidate) || adjacency.isAdjacent(candidate, picked))) {
            chosen.add(candidate);
            best = largestSpread(positions, adjacency, index + 1, chosen, target, best);
            chosen.removeLast();
            if (best >= target) return best;
        }
        return largestSpread(positions, adjacency, index + 1, chosen, target, best);
    }
}
