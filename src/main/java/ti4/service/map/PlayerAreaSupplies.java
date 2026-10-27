package ti4.service.map;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;

public final class PlayerAreaSupplies {

    public static final int GALVANIZE_TOKENS = 7;
    public static final int BREACH_TOKENS = 7;
    public static final int SEVER_TOKENS = 1;

    private PlayerAreaSupplies() {}

    public static Map<UnitKey, Integer> unitsOnBoard(Game game) {
        Map<UnitKey, Integer> unitsOnBoard = new HashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                count(unitsOnBoard, unitHolder, false);
            }
        }
        for (Player player : game.getPlayers().values()) {
            UnitHolder nombox = player.getNomboxTile().getSpaceUnitHolder();
            if (nombox == null) continue;
            count(unitsOnBoard, nombox, true);
        }
        return unitsOnBoard;
    }

    private static void count(
            Map<UnitKey, Integer> unitsOnBoard, UnitHolder unitHolder, boolean ignoreInfantryFighters) {
        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            int count = unitsOnBoard.getOrDefault(unitKey, 0);
            if (unitKey.unitType() == UnitType.Infantry || unitKey.unitType() == UnitType.Fighter) {
                if (ignoreInfantryFighters) continue;
                count++;
            } else {
                count += unitHolder.getUnitCount(unitKey);
            }
            unitsOnBoard.put(unitKey, count);
        }
    }

    public static int galvanizedCount(Game game) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .mapToInt(UnitHolder::getTotalGalvanizedCount)
                .sum();
    }

    public static boolean galvanizeOverLimit(Game game, int galvanized) {
        return !game.isFrankenGame() && !hasNonBastionGalvanizeSource(game) && galvanized > GALVANIZE_TOKENS;
    }

    private static boolean hasNonBastionGalvanizeSource(Game game) {
        return game.getRealPlayers().stream()
                .anyMatch(player -> player.hasTech("thardentiar")
                        || player.hasTech("thponthousr")
                        || player.ownsPromissoryNote("thpnponthous")
                        || player.hasUnlockedBreakthrough("kryxosbt"));
    }

    public static int breachCount(Game game) {
        return (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .flatMap(unitHolder -> unitHolder.getTokenList().stream())
                .filter(token ->
                        Constants.TOKEN_BREACH_ACTIVE.equals(token) || Constants.TOKEN_BREACH_INACTIVE.equals(token))
                .count();
    }

    public static int severCount(Game game) {
        return (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .filter(unitHolder -> unitHolder.getTokenList().contains(Constants.TOKEN_SEVERED))
                .count();
    }

    public static List<String> positionsWithUnit(Game game, UnitKey unitKey) {
        return positionsWhere(game, unitHolder -> unitHolder.getUnitCount(unitKey) > 0);
    }

    public static List<String> breachPositions(Game game) {
        return positionsWhere(
                game,
                unitHolder -> unitHolder.getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE)
                        || unitHolder.getTokenList().contains(Constants.TOKEN_BREACH_INACTIVE));
    }

    public static List<String> severPositions(Game game) {
        return positionsWhere(game, unitHolder -> unitHolder.getTokenList().contains(Constants.TOKEN_SEVERED));
    }

    public static List<String> galvanizedPositions(Game game) {
        return positionsWhere(game, unitHolder -> unitHolder.getTotalGalvanizedCount() > 0);
    }

    private static List<String> positionsWhere(Game game, Predicate<UnitHolder> matches) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getUnitHolders().values().stream().anyMatch(matches))
                .map(Tile::getPosition)
                .sorted()
                .toList();
    }
}
