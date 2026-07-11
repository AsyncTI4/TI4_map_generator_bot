package ti4.service.unit;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;

/** Read-only queries for units on the map and, when requested, in player nomboxes. */
@UtilityClass
public class UnitQueryService {

    public static List<Tile> getTilesContainingPlayersUnits(Game game, Player player, UnitType... unitTypes) {
        List<UnitType> requestedTypes = Arrays.asList(unitTypes);
        return game.getTiles().stream()
                .filter(tile -> tile.containsPlayersUnitsWithKeyCondition(
                        player, unit -> requestedTypes.contains(unit.unitType())))
                .toList();
    }

    public static int countUnitsOnBoard(Game game, Player player, UnitType type) {
        return findUnits(game, player, key -> key.unitType() == type)
                .mapToInt(UnitLocation::count)
                .sum();
    }

    public static int countUnitsOnBoard(Game game, UnitKey unitKey) {
        return countUnits(game, unitKey, false);
    }

    /** Counts this player's unit model on the map and in nomboxes, matching the physical-piece total. */
    public static int countUnits(Game game, Player player, String unit) {
        return countUnits(game, player, unit, true);
    }

    /** Counts this player's unit model, optionally including units held in player nomboxes. */
    public static int countUnits(Game game, Player player, String unit, boolean includeNomboxes) {
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        return countUnits(game, unitKey, includeNomboxes);
    }

    /** Counts one exact unit key on the map and in nomboxes. */
    public static int countUnits(Game game, UnitKey unitKey) {
        return countUnits(game, unitKey, true);
    }

    public static int countUnits(Game game, UnitKey unitKey, boolean includeNomboxes) {
        return streamUnitHolders(game, includeNomboxes)
                .mapToInt(holder -> holder.getUnitCount(unitKey))
                .sum();
    }

    public static int countUnitsInSystem(Player player, Tile tile, UnitType... unitTypes) {
        List<UnitType> requestedTypes = Arrays.asList(unitTypes);
        return tile.getUnitHolderValues().stream()
                .flatMap(holder -> holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> requestedTypes.contains(key.unitType()))
                        .map(key -> holder.getUnitCount(key)))
                .mapToInt(Integer::intValue)
                .sum();
    }

    public static boolean hasUnitsInSystem(Player player, Tile tile, UnitType... unitTypes) {
        return countUnitsInSystem(player, tile, unitTypes) > 0;
    }

    /**
     * Whether this player's units appear in a system adjacent to {@code tile}, using that player's adjacency
     * modifiers.
     */
    public static boolean hasUnitsInAdjacentSystems(Game game, Player player, Tile tile, UnitType... unitTypes) {
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacentTile -> hasUnitsInSystem(player, adjacentTile, unitTypes));
    }

    public static Stream<UnitLocation> findUnits(Game game, Player player, Predicate<UnitKey> predicate) {
        return game.streamUnitHolderLocations()
                .flatMap(location -> location.unitHolder().getUnitKeysForPlayer(player).stream()
                        .filter(predicate)
                        .map(key -> new UnitLocation(
                                location.tile(),
                                location.unitHolder(),
                                key,
                                location.unitHolder().getUnitCount(key))));
    }

    private static Stream<UnitHolder> streamUnitHolders(Game game, boolean includeNomboxes) {
        Stream<UnitHolder> boardHolders = game.streamUnitHolders();
        if (!includeNomboxes) {
            return boardHolders;
        }
        Stream<UnitHolder> nomboxHolders =
                game.getRealPlayers().stream().flatMap(player -> player.getNomboxTile().getUnitHolderValues().stream());
        return Stream.concat(boardHolders, nomboxHolders);
    }
}
