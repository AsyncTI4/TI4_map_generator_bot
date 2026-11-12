package ti4.service.map;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

@UtilityClass
public class RefractureRulesService {

    private static final String FENRIR_KEY = "refractureFenrir";
    private static final String CHARON_MOVEMENT_KEY = "refractureCharonMovement_";
    private static final String DEMETER_CAPACITY_KEY = "refractureDemeterCapacity_";
    private static final String LEKYTHOI_PENDING_KEY = "refractureLekythoiPending_";
    private static final String MAAHES_KEY = "refractureMaahes_";

    public static void activateFenrir(Game game, Player player) {
        if (!isFenrirActivationWindow(game, player)) return;
        game.setStoredValue(FENRIR_KEY, player.getFaction());
    }

    public static boolean canUseFenrir(Game game, Player player) {
        return isFenrirActivationWindow(game, player)
                && !player.getExhaustedPlanetsAbilities().contains("fenrir");
    }

    private static boolean isFenrirActivationWindow(Game game, Player player) {
        return game.isRefractureMode()
                && player == game.getActivePlayer()
                && !game.getActiveSystem().isEmpty()
                && player.getPlanets().contains("fenrir");
    }

    public static void clearTacticalState(Game game) {
        game.removeStoredValue(FENRIR_KEY);
        game.getRealPlayers().forEach(player -> {
            game.removeStoredValue(CHARON_MOVEMENT_KEY + player.getFaction());
            game.removeStoredValue(MAAHES_KEY + player.getFaction());
        });
    }

    public static boolean canUseMaahes(Game game, Player player) {
        return game.isRefractureMode()
                && player == game.getActivePlayer()
                && player.getPlanets().contains("maahes")
                && !player.getExhaustedPlanetsAbilities().contains("maahes")
                && !getEligibleMaahesTiles(game).isEmpty();
    }

    public static List<Tile> getEligibleMaahesTiles(Game game) {
        String frontierToken = Mapper.getTokenID(Constants.FRONTIER);
        return game.getTileMap().values().stream()
                .filter(Tile::isFracture)
                .filter(tile -> tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> !tile.getUnitHolders()
                        .get(Constants.SPACE)
                        .getTokenList()
                        .contains(frontierToken))
                .toList();
    }

    public static void startMaahes(Game game, Player player) {
        game.setStoredValue(MAAHES_KEY + player.getFaction(), "active");
    }

    public static boolean canPlaceMaahesFrontier(Game game, Player player, Tile tile) {
        return game.isRefractureMode()
                && player == game.getActivePlayer()
                && player.getPlanets().contains("maahes")
                && hasPendingMaahes(game, player)
                && getEligibleMaahesTiles(game).contains(tile);
    }

    public static boolean placeMaahesFrontier(Game game, Player player, Tile tile) {
        if (!canPlaceMaahesFrontier(game, player, tile)) {
            return false;
        }
        tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
        return true;
    }

    public static boolean hasPendingMaahes(Game game, Player player) {
        return "active".equals(game.getStoredValue(MAAHES_KEY + player.getFaction()));
    }

    public static void clearPendingMaahes(Game game, Player player) {
        game.removeStoredValue(MAAHES_KEY + player.getFaction());
    }

    public static void addFenrirAdjacencies(Game game, Player player, String position, Set<String> adjacent) {
        if (!game.isRefractureMode() || !player.getFaction().equals(game.getStoredValue(FENRIR_KEY))) return;
        Tile origin = game.getTileByPosition(position);
        if (!isNonHomeEdgeTile(game, origin)) return;
        game.getTileMap().values().stream()
                .filter(tile -> isNonHomeEdgeTile(game, tile))
                .map(Tile::getPosition)
                .forEach(adjacent::add);
    }

    private static boolean isNonHomeEdgeTile(Game game, Tile tile) {
        if (tile == null || tile.isHomeSystem(game)) return false;
        if (!tile.isFracture()) return tile.isEdgeOfBoard(game);
        return MapPositionMapper.getAdjacentTilePositions(tile.getPosition()).stream()
                .anyMatch(position -> !"x".equals(position) && game.getTileByPosition(position) == null);
    }

    public static boolean charonCanParticipateInCombat(Game game, Player player, Tile tile) {
        Tile charonTile = game.getTileFromPlanet("charon");
        return game.isRefractureMode()
                && !player.isNeutral()
                && player.getPlanets().contains("charon")
                && !player.getExhaustedPlanets().contains("charon")
                && charonTile == tile
                && tile != null
                && tile.getUnitHolderFromPlanet("charon") != null;
    }

    public static void recordCharonShipMovement(Game game, Player player, Tile destination) {
        Tile charonTile = game.getTileFromPlanet("charon");
        if (!game.isRefractureMode()
                || charonTile == null
                || destination.isHomeSystem(game)
                || !player.getPlanets().contains("charon")) {
            return;
        }
        boolean movedShip = game.getTacticalActionDisplacement().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(charonTile.getPosition() + "-"))
                .flatMap(entry -> entry.getValue().keySet().stream())
                .filter(player::unitBelongsToPlayer)
                .map(player::getUnitFromUnitKey)
                .filter(java.util.Objects::nonNull)
                .anyMatch(unit -> unit.getIsShip());
        if (movedShip) {
            game.setStoredValue(CHARON_MOVEMENT_KEY + player.getFaction(), destination.getPosition());
        }
    }

    public static boolean canMoveCharonTo(Game game, Player player, Tile destination) {
        return game.isRefractureMode()
                && player.getPlanets().contains("charon")
                && destination != null
                && destination.getPosition().equals(game.getActiveSystem())
                && destination.getPosition().equals(game.getStoredValue(CHARON_MOVEMENT_KEY + player.getFaction()));
    }

    public static void consumeCharonMovement(Game game, Player player) {
        game.removeStoredValue(CHARON_MOVEMENT_KEY + player.getFaction());
    }

    public static boolean canDoubleDemeterCapacity(Game game, Player player) {
        return game.isRefractureMode()
                && player.getPlanets().contains("demeterii")
                && game.getStoredValue(DEMETER_CAPACITY_KEY + player.getFaction())
                        .isEmpty();
    }

    public static void doubleDemeterCapacity(Game game, Player player) {
        if (canDoubleDemeterCapacity(game, player)) {
            game.setStoredValue(DEMETER_CAPACITY_KEY + player.getFaction(), "active");
        }
    }

    public static boolean hasDoubledDemeterCapacity(Game game, Player player) {
        return game.isRefractureMode()
                && player.getPlanets().contains("demeterii")
                && "active".equals(game.getStoredValue(DEMETER_CAPACITY_KEY + player.getFaction()));
    }

    public static void clearDemeterCapacity(Game game, Player player) {
        game.removeStoredValue(DEMETER_CAPACITY_KEY + player.getFaction());
    }

    public static boolean canUseExperiment(Game game, Player player) {
        return game.isRefractureMode()
                && player.getPlanets().contains("experiment")
                && !player.getExhaustedPlanetsAbilities().contains("experiment")
                && Stream.concat(
                                game.getPublicObjectives1Peekable().stream(),
                                game.getPublicObjectives2Peekable().stream())
                        .anyMatch(
                                objective -> !game.getRevealedPublicObjectives().containsKey(objective));
    }

    public static boolean canUseLekythoi(Game game, Player player) {
        return game.isRefractureMode()
                && player.isPassed()
                && player.getPlanets().contains("lekythoi")
                && !player.getExhaustedPlanetsAbilities().contains("lekythoi");
    }

    public static String startLekythoi(Game game, Player player) {
        String token = Integer.toString(game.getRound());
        game.setStoredValue(LEKYTHOI_PENDING_KEY + player.getFaction(), token);
        return token;
    }

    public static boolean hasPendingLekythoi(Game game, Player player, String token) {
        return game.isRefractureMode()
                && player.getPlanets().contains("lekythoi")
                && player.getExhaustedPlanetsAbilities().contains("lekythoi")
                && token != null
                && token.equals(game.getStoredValue(LEKYTHOI_PENDING_KEY + player.getFaction()));
    }

    public static void clearPendingLekythoi(Game game, Player player) {
        game.removeStoredValue(LEKYTHOI_PENDING_KEY + player.getFaction());
    }
}
