package ti4.ai.secrets;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.MovementGraph;
import ti4.ai.tactical.CannonWipeRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class ActionSecretOdds {

    private static final String DEMONSTRATE_YOUR_POWER = "dyp";
    private static final String BRAVE_THE_VOID = "btv";
    private static final String UNVEIL_FLAGSHIP = "uf";
    private static final String SPARK_A_REBELLION = "sar";
    private static final String PROVE_ENDURANCE = "pe";
    private static final String MAKE_AN_EXAMPLE = "mew";
    private static final String FIGHT_WITH_PRECISION = "fwp";
    private static final String BETRAY_A_FRIEND = "baf";
    private static final String DARKEN_THE_SKIES = "dts";
    private static final String DESTROY_THEIR_GREATEST_SHIP = "dtgs";
    private static final String TURN_THEIR_FLEETS_TO_DUST = "ttfd";
    private static final String BECOME_A_MARTYR = "bam";
    private static final int DEMONSTRATION_SHIPS = 3;
    private static final int FLEET_MOVE = 1;
    private static final double ENGINEERABLE = 0.60;
    private static final double DEMONSTRATION_EVIDENCED = 0.30;
    private static final double VOID_EVIDENCED = 0.18;
    private static final double FLAGSHIP_EVIDENCED = 0.19;
    private static final Map<String, Double> EVIDENCED_CAP = Map.of(
            DEMONSTRATE_YOUR_POWER, DEMONSTRATION_EVIDENCED,
            BRAVE_THE_VOID, VOID_EVIDENCED,
            UNVEIL_FLAGSHIP, FLAGSHIP_EVIDENCED);
    private static final double OTHER_SECRET = 0.05;
    private static final double MARTYR_UNPRESSED = 0.03;
    private static final double MARTYR_HOME_THREATENED = 0.20;
    private static final double MARTYR_RAID_HELD = 0.30;
    private static final double DUST_TARGET_IN_RANGE = 0.30;

    public static double perRound(Game game, Player seat, String secretId) {
        return switch (secretId) {
            case DEMONSTRATE_YOUR_POWER -> demonstrateYourPower(game, seat);
            case BRAVE_THE_VOID -> braveTheVoid(game, seat);
            case UNVEIL_FLAGSHIP -> unveilFlagship(game, seat);
            case SPARK_A_REBELLION -> isSoleLeader(game, seat) ? 0.08 : 0.21;
            case PROVE_ENDURANCE -> 0.17;
            case MAKE_AN_EXAMPLE -> hasBombardmentShips(game, seat) ? 0.155 : 0.08;
            case FIGHT_WITH_PRECISION -> hasOnBoard(game, seat, "dd") ? 0.13 : 0.06;
            case BETRAY_A_FRIEND -> holdsAnotherPlayersNote(game, seat) ? 0.125 : 0.05;
            case DARKEN_THE_SKIES -> hasShipsBesideAnotherHome(game, seat) ? 0.11 : 0.05;
            case DESTROY_THEIR_GREATEST_SHIP -> enemyHeavyShipsExist(game, seat) ? 0.10 : 0.03;
            case TURN_THEIR_FLEETS_TO_DUST -> turnTheirFleetsToDust(game, seat);
            case BECOME_A_MARTYR -> becomeAMartyr(game, seat);
            default -> OTHER_SECRET;
        };
    }

    public static double lastingPerRound(Game game, Player seat, String secretId) {
        return Math.min(perRound(game, seat, secretId), EVIDENCED_CAP.getOrDefault(secretId, 1.0));
    }

    public static double beforeGameEnds(Game game, Player seat, String secretId) {
        return atLeastOnce(perRound(game, seat, secretId), GameHorizon.roundsLeft(game));
    }

    public static double atLeastOnce(double perRound, double opportunities) {
        return 1 - Math.pow(1 - perRound, opportunities);
    }

    private static double demonstrateYourPower(Game game, Player seat) {
        List<Tile> stacks =
                tilesWhere(game, tile -> BoardView.nonFighterShips(BoardView.space(tile), seat) >= DEMONSTRATION_SHIPS);
        if (stacks.stream()
                .anyMatch(stack -> reaches(game, seat, stack, FLEET_MOVE, tile -> hasEnemyShips(game, seat, tile)))) {
            return ENGINEERABLE;
        }
        return stacks.isEmpty() ? 0.21 : DEMONSTRATION_EVIDENCED;
    }

    private static double braveTheVoid(Game game, Player seat) {
        List<Tile> fleets = tilesWhere(game, tile -> BoardView.hasOwnShips(seat, tile));
        if (fleets.stream()
                .anyMatch(fleet -> reaches(
                        game,
                        seat,
                        fleet,
                        FLEET_MOVE,
                        tile -> tile.isAnomaly(game, seat) && hasEnemyShips(game, seat, tile)))) {
            return ENGINEERABLE;
        }
        return fleets.stream().anyMatch(fleet -> inOrBesideAnomaly(game, seat, fleet)) ? VOID_EVIDENCED : 0.09;
    }

    private static double turnTheirFleetsToDust(Game game, Player seat) {
        if (CannonWipeRules.hasCoveredTarget(game, seat)) return DUST_TARGET_IN_RANGE;
        return hasOnBoard(game, seat, "pds") ? 0.085 : 0.03;
    }

    private static double becomeAMartyr(Game game, Player seat) {
        if (holdsPlanetInAnotherHome(game, seat)) return MARTYR_RAID_HELD;
        return ownHomeOutmatchedNextDoor(game, seat) ? MARTYR_HOME_THREATENED : MARTYR_UNPRESSED;
    }

    private static boolean holdsPlanetInAnotherHome(Game game, Player seat) {
        return seat.getPlanets().stream()
                .map(game::getTileFromPlanet)
                .anyMatch(tile -> tile != null && tile.isHomeSystem(game) && tile != seat.getHomeSystemTile());
    }

    private static boolean ownHomeOutmatchedNextDoor(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        if (home == null
                || home.getPlanetUnitHolders().stream()
                        .noneMatch(planet -> seat.getPlanets().contains(planet.getName()))) {
            return false;
        }
        double defence = BoardView.shipCost(BoardView.space(home), seat);
        return FoWHelper.getAdjacentTiles(game, home.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .filter(near -> near != null)
                .anyMatch(near -> game.getRealPlayers().stream()
                        .filter(other -> other != seat)
                        .anyMatch(other -> BoardView.shipCost(BoardView.space(near), other) > defence));
    }

    private static double unveilFlagship(Game game, Player seat) {
        List<Tile> flagships = tilesWhere(game, tile -> BoardView.countInTile(tile, seat, UnitType.Flagship) > 0);
        int move = BoardView.moveValueWithGravityDrive(seat, UnitType.Flagship);
        if (flagships.stream()
                .anyMatch(ship -> reaches(game, seat, ship, move, tile -> hasEnemyShips(game, seat, tile)))) {
            return ENGINEERABLE;
        }
        return flagships.isEmpty() ? 0.10 : FLAGSHIP_EVIDENCED;
    }

    private static boolean reaches(Game game, Player seat, Tile origin, int move, Predicate<Tile> target) {
        return MovementGraph.reach(game, seat, origin.getPosition(), move).keySet().stream()
                .map(game::getTileByPosition)
                .anyMatch(tile -> tile != null && target.test(tile));
    }

    private static boolean inOrBesideAnomaly(Game game, Player seat, Tile tile) {
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(near -> near != null && near.isAnomaly(game, seat));
    }

    private static boolean hasShipsBesideAnotherHome(Game game, Player seat) {
        return tilesWhere(game, tile -> tile.isHomeSystem(game) && tile != seat.getHomeSystemTile()).stream()
                .flatMap(home -> FoWHelper.getAdjacentTiles(game, home.getPosition(), seat, false).stream())
                .map(game::getTileByPosition)
                .anyMatch(near -> near != null && BoardView.hasOwnShips(seat, near));
    }

    private static boolean holdsAnotherPlayersNote(Game game, Player seat) {
        return seat.getPromissoryNotesInPlayArea().stream()
                .map(game::getPNOwner)
                .anyMatch(owner -> owner != null && owner != seat);
    }

    private static boolean isSoleLeader(Game game, Player seat) {
        return seat.getTotalVictoryPoints() > GameHorizon.highestOtherPoints(game, seat);
    }

    private static boolean hasEnemyShips(Game game, Player seat, Tile tile) {
        return BoardView.hasEnemyShips(game, seat, tile);
    }

    private static List<Tile> tilesWhere(Game game, Predicate<Tile> filter) {
        return game.getTileMap().values().stream().filter(filter).toList();
    }

    private static boolean hasOnBoard(Game game, Player seat, String asyncId) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId) > 0;
    }

    private static boolean hasBombardmentShips(Game game, Player seat) {
        return BoardView.MOVING_SHIPS.stream()
                .map(seat::getUnitByType)
                .filter(model ->
                        model != null && model.getCombatDieCountForAbility(CombatRollType.bombardment, seat) > 0)
                .anyMatch(model -> hasOnBoard(game, seat, model.getAsyncId()));
    }

    private static boolean enemyHeavyShipsExist(Game game, Player seat) {
        return game.getTileMap().values().stream()
                .anyMatch(tile -> anotherPlayerHasHeavyShips(game, seat, BoardView.space(tile)));
    }

    private static boolean anotherPlayerHasHeavyShips(Game game, Player seat, UnitHolder space) {
        if (space == null) return false;
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .anyMatch(other -> BoardView.count(space, other, UnitType.Flagship)
                                + BoardView.count(space, other, UnitType.Warsun)
                        > 0);
    }
}
